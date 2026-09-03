package io.wiretap.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wiretap.kafka.message.KafkaMessageInfo;
import io.wiretap.kafka.message.KafkaRecordLogFilter;
import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaConsumerLogMessageSettings;
import io.wiretap.kafka.message.settings.SpecificKafkaInfoLogMessageSettings;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.metrics.WiretapMetricsImpl;
import io.wiretap.metrics.WiretapMetricsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link KafkaRecordLogFilter} SPI: a registered bean decides per
 * record whether the log line is written, sees the record before masking, and
 * a filter that throws leaves the record in the log instead of silencing it.
 *
 * <p>Also covers {@code enable-record-filtering}: the bean only runs where the
 * effective per-topic settings admit it.
 */
class KafkaLogSinkFilterTest {

    private SimpleMeterRegistry registry;
    private WiretapMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        WiretapMetricsProperties props = new WiretapMetricsProperties();
        props.setDetailedTimings(true);
        metrics = new WiretapMetricsImpl(registry, props);
    }

    @Test
    void recordAdmittedByTheFilterIsLogged() {
        sink(record -> "{\"requestSource\":\"system-1\"}".equals(record.getKey()))
                .emit(info("{\"requestSource\":\"system-1\"}"));

        assertThat(emitted()).as("admitted record should reach the log").isEqualTo(1L);
    }

    @Test
    void recordRejectedByTheFilterIsNotLogged() {
        sink(record -> false).emit(info("{\"requestSource\":\"system-7\"}"));

        assertThat(emitted()).as("rejected record should never be serialised").isZero();
    }

    @Test
    void rejectedRecordIsCountedAsFiltered() {
        sink(record -> false).emit(info("{\"requestSource\":\"system-7\"}"));

        assertThat(skipped("filter_bean")).as("rejected record should be counted under filter_bean").isEqualTo(1L);
    }

    @Test
    void filterSeesTheValueBeforeMasking() {
        AtomicReference<String> seen = new AtomicReference<>();
        KafkaValueMaskingHandler masking = (topic, value) -> "***";
        new KafkaLogSink(new KafkaConsumerLogMessageSettings(), new KafkaAccessFieldNames(),
                masking, null, null, record -> seen.compareAndSet(null, record.getValue()), metrics)
                .emit(info("plain-key"));

        assertThat(seen.get()).as("filter should read the raw value, not the masked one")
                .isEqualTo("{\"orderId\":\"ord-42\"}");
    }

    @Test
    void throwingFilterKeepsTheRecordInTheLog() {
        sink(record -> {
            throw new IllegalStateException("broken filter");
        }).emit(info("any-key"));

        assertThat(emitted()).as("broken filter should not silence the log").isEqualTo(1L);
    }

    @Test
    void throwingFilterIsCountedAsFilterError() {
        sink(record -> {
            throw new IllegalStateException("broken filter");
        }).emit(info("any-key"));

        assertThat(skipped("filter_error")).as("filter failure should be visible in metrics").isEqualTo(1L);
    }

    @Test
    void sinkWithoutFilterLogsEveryRecord() {
        new KafkaLogSink(new KafkaConsumerLogMessageSettings(), new KafkaAccessFieldNames(),
                null, null, null, null, metrics)
                .emit(info("any-key"));

        assertThat(emitted()).as("absent filter bean should keep 2.0.x behaviour").isEqualTo(1L);
    }

    @Test
    void filterIsIgnoredWhenFilteringIsDisabledGlobally() {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.setEnableRecordFiltering(false);

        sink(settings, record -> false).emit(info("any-key"));

        assertThat(emitted()).as("filtering switched off in config should leave the bean unused").isEqualTo(1L);
    }

    @Test
    void filterAppliesOnTopicThatSwitchesFilteringBackOn() {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.setEnableRecordFiltering(false);
        settings.setSpecificTopicSettings(List.of(filtering("orders\\..*", true)));

        sink(settings, record -> false).emit(info("any-key"));

        assertThat(emitted()).as("per-topic override should switch filtering back on").isZero();
    }

    @Test
    void filterIsIgnoredOnTopicThatSwitchesFilteringOff() {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.setSpecificTopicSettings(List.of(filtering("orders\\..*", false)));

        sink(settings, record -> false).emit(info("any-key"));

        assertThat(emitted()).as("per-topic override should switch filtering off").isEqualTo(1L);
    }

    private KafkaLogSink sink(KafkaRecordLogFilter filter) {
        return sink(new KafkaConsumerLogMessageSettings(), filter);
    }

    private KafkaLogSink sink(KafkaConsumerLogMessageSettings settings, KafkaRecordLogFilter filter) {
        return new KafkaLogSink(settings, new KafkaAccessFieldNames(),
                null, null, null, filter, metrics);
    }

    private static SpecificKafkaInfoLogMessageSettings filtering(String pattern, boolean enabled) {
        SpecificKafkaInfoLogMessageSettings override = new SpecificKafkaInfoLogMessageSettings();
        override.setMatchTopicPattern(pattern);
        override.setEnableRecordFiltering(enabled);
        return override;
    }

    private KafkaMessageInfo info(String key) {
        return KafkaMessageInfo.builder()
                .direction(KafkaMessageInfo.Direction.INCOMING)
                .topic("orders.events")
                .key(key)
                .value("{\"orderId\":\"ord-42\"}")
                .valueLength(24L)
                .status(KafkaMessageInfo.Status.SUCCESS)
                .build();
    }

    private long skipped(String reason) {
        Counter counter = registry.find("wiretap.kafka.skipped")
                .tag("direction", "consumer")
                .tag("reason", reason)
                .counter();
        return counter == null ? 0L : (long) counter.count();
    }

    private long emitted() {
        DistributionSummary summary = registry.find("wiretap.kafka.message.size").summary();
        return summary == null ? 0L : summary.count();
    }
}

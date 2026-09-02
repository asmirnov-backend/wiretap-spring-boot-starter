package io.wiretap.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wiretap.kafka.message.KafkaMessageInfo;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaConsumerLogMessageSettings;
import io.wiretap.kafka.message.settings.SpecificKafkaInfoLogMessageSettings;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.metrics.WiretapMetricsImpl;
import io.wiretap.metrics.WiretapMetricsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code key-json-include} record filter: only records whose key
 * parses as JSON and matches every configured JSON Pointer reach the log, the
 * rest are counted as {@code wiretap.kafka.skipped{reason="filter_key"}} and
 * never pay for masking or serialisation.
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
    void recordWithMatchingKeyFieldIsLogged() {
        sink(Map.of("/requestSource", List.of("system-1")))
                .emit(info("{\"requestSource\":\"system-1\",\"id\":91}"));

        assertThat(emitted()).as("matching record should reach the log").isEqualTo(1L);
    }

    @Test
    void recordWithForeignKeyFieldIsSkipped() {
        sink(Map.of("/requestSource", List.of("system-1")))
                .emit(info("{\"requestSource\":\"system-7\"}"));

        assertThat(skipped()).as("foreign requestSource should be counted as filtered").isEqualTo(1L);
    }

    @Test
    void skippedRecordIsNotSerialised() {
        sink(Map.of("/requestSource", List.of("system-1")))
                .emit(info("{\"requestSource\":\"system-7\"}"));

        assertThat(emitted()).as("filtered record should never be serialised").isZero();
    }

    @Test
    void recordWithNonJsonKeyIsSkipped() {
        sink(Map.of("/requestSource", List.of("system-1")))
                .emit(info("plain-text-key"));

        assertThat(skipped()).as("non-JSON key cannot satisfy an include condition").isEqualTo(1L);
    }

    @Test
    void recordWithoutKeyIsSkipped() {
        sink(Map.of("/requestSource", List.of("system-1"))).emit(info(null));

        assertThat(skipped()).as("null key cannot satisfy an include condition").isEqualTo(1L);
    }

    @Test
    void recordMissingThePointedFieldIsSkipped() {
        sink(Map.of("/requestSource", List.of("system-1")))
                .emit(info("{\"tenant\":\"acme\"}"));

        assertThat(skipped()).as("absent pointer should not admit the record").isEqualTo(1L);
    }

    @Test
    void valuesAreTreatedAsRegex() {
        sink(Map.of("/requestSource", List.of("system-\\d+")))
                .emit(info("{\"requestSource\":\"system-42\"}"));

        assertThat(emitted()).as("regex value should admit the record").isEqualTo(1L);
    }

    @Test
    void partialRegexMatchDoesNotAdmitTheRecord() {
        sink(Map.of("/requestSource", List.of("system-1")))
                .emit(info("{\"requestSource\":\"system-13\"}"));

        assertThat(skipped()).as("regex should match the whole value").isEqualTo(1L);
    }

    @Test
    void anyValueOfTheListAdmitsTheRecord() {
        sink(Map.of("/requestSource", List.of("system-1", "system-2")))
                .emit(info("{\"requestSource\":\"system-2\"}"));

        assertThat(emitted()).as("list of values should behave as OR").isEqualTo(1L);
    }

    @Test
    void everyPointerMustMatch() {
        sink(Map.of("/requestSource", List.of("system-1"), "/tenant", List.of("acme")))
                .emit(info("{\"requestSource\":\"system-1\",\"tenant\":\"globex\"}"));

        assertThat(skipped()).as("several pointers should behave as AND").isEqualTo(1L);
    }

    @Test
    void nestedPointerIsResolved() {
        sink(Map.of("/meta/requestSource", List.of("system-1")))
                .emit(info("{\"meta\":{\"requestSource\":\"system-1\"}}"));

        assertThat(emitted()).as("nested pointer should be resolved").isEqualTo(1L);
    }

    @Test
    void numericKeyFieldIsMatchedByItsTextForm() {
        sink(Map.of("/version", List.of("3")))
                .emit(info("{\"version\":3}"));

        assertThat(emitted()).as("numeric field should match its text form").isEqualTo(1L);
    }

    @Test
    void emptyFilterLogsEverything() {
        sink(Map.of()).emit(info("whatever-key"));

        assertThat(emitted()).as("default settings should keep 1.0.x behaviour").isEqualTo(1L);
    }

    @Test
    void topicSpecificFilterOverridesTheCommonOne() {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.setKeyJsonInclude(Map.of("/requestSource", List.of("system-1")));
        SpecificKafkaInfoLogMessageSettings specific = new SpecificKafkaInfoLogMessageSettings();
        specific.setMatchTopicPattern("orders\\..*");
        specific.setKeyJsonInclude(Map.of("/requestSource", List.of("system-9")));
        settings.setSpecificTopicSettings(List.of(specific));

        new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null, metrics)
                .emit(info("{\"requestSource\":\"system-9\"}"));

        assertThat(emitted()).as("per-topic filter should win over the common one").isEqualTo(1L);
    }

    private KafkaLogSink sink(Map<String, List<String>> include) {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.setKeyJsonInclude(include);
        return new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null, metrics);
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

    private long skipped() {
        Counter counter = registry.find("wiretap.kafka.skipped")
                .tag("direction", "consumer")
                .tag("reason", "filter_key")
                .counter();
        return counter == null ? 0L : (long) counter.count();
    }

    private long emitted() {
        DistributionSummary summary = registry.find("wiretap.kafka.message.size").summary();
        return summary == null ? 0L : summary.count();
    }
}

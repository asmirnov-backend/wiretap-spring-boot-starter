package io.wiretap.kafka;

import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaProducerLogMessageSettings;
import io.wiretap.kafka.message.settings.SpecificKafkaInfoLogMessageSettings;
import io.wiretap.metrics.NoOpWiretapMetrics;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Header selection has to honour per-topic overrides, including the {@code *} wildcard —
 * that is what the README and the 0.1.5 release notes promise for
 * {@code specific-topic-settings}.
 */
class KafkaLogSinkHeadersTest {

    @Test
    void perTopicOverrideNarrowsTheHeaderList() {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.setHeaders(List.of("x-trace-id"));
        settings.setSpecificTopicSettings(List.of(headersOverride("orders\\..*", List.of("x-tenant"))));

        Map<String, String> orders = sink(settings).collectHeaders("orders.events", headers());
        assertThat(orders).containsOnlyKeys("x-tenant");

        Map<String, String> other = sink(settings).collectHeaders("demo.events", headers());
        assertThat(other).containsOnlyKeys("x-trace-id");
    }

    @Test
    void perTopicWildcardLogsEveryHeader() {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.setHeaders(List.of("x-trace-id"));
        settings.setSpecificTopicSettings(List.of(headersOverride("debug\\..*", List.of("*"))));

        assertThat(sink(settings).collectHeaders("debug.events", headers()))
                .containsOnlyKeys("x-trace-id", "x-tenant", "x-secret");
    }

    @Test
    void commonHeadersApply_whenOverrideConfiguresNone() {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.setHeaders(List.of("x-trace-id"));

        SpecificKafkaInfoLogMessageSettings override = new SpecificKafkaInfoLogMessageSettings();
        override.setMatchTopicPattern("orders\\..*");
        settings.setSpecificTopicSettings(List.of(override));

        assertThat(sink(settings).collectHeaders("orders.events", headers()))
                .containsOnlyKeys("x-trace-id");
    }

    private static KafkaLogSink sink(KafkaProducerLogMessageSettings settings) {
        return new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null, new NoOpWiretapMetrics());
    }

    private static SpecificKafkaInfoLogMessageSettings headersOverride(String pattern, List<String> headers) {
        SpecificKafkaInfoLogMessageSettings override = new SpecificKafkaInfoLogMessageSettings();
        override.setMatchTopicPattern(pattern);
        override.setHeaders(headers);
        return override;
    }

    private static Headers headers() {
        RecordHeaders headers = new RecordHeaders();
        headers.add("x-trace-id", "trace-1".getBytes(StandardCharsets.UTF_8));
        headers.add("x-tenant", "acme".getBytes(StandardCharsets.UTF_8));
        headers.add("x-secret", "hunter2".getBytes(StandardCharsets.UTF_8));
        return headers;
    }
}

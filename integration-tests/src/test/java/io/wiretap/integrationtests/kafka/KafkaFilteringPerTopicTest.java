package io.wiretap.integrationtests.kafka;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import io.wiretap.kafka.message.KafkaRecordLogFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Record filtering is off globally and switched back on for one topic family. This is
 * what lets a single filter bean stay registered while configuration decides which
 * topics it actually applies to.
 */
@TestPropertySource(properties = {
        "wiretap.kafka-producer-interceptor.enable-record-filtering=false",
        "wiretap.kafka-producer-interceptor.specific-topic-settings[0].match-topic-pattern=orders\\..*",
        "wiretap.kafka-producer-interceptor.specific-topic-settings[0].enable-record-filtering=true"
})
class KafkaFilteringPerTopicTest extends WiretapIntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void perTopicOverrideActivatesTheFilter(CapturedOutput output) {
        kafkaTemplate.send("orders.events", "filter-key", "drop-me");
        kafkaTemplate.send("orders.events", "filter-key", "keep-me");

        JsonLogCapture.awaitMatching(output, e -> outgoing(e)
            && "keep-me".equals(JsonLogCapture.at(e, "kafka_info.value")));

        assertThat(JsonLogCapture.kafkaInfo(output).stream().filter(KafkaFilteringPerTopicTest::outgoing))
            .as("filtering switched on for this topic should drop the rejected record")
            .noneMatch(e -> "drop-me".equals(JsonLogCapture.at(e, "kafka_info.value")));
    }

    @Test
    void topicsOutsideThePatternKeepFilteringOff(CapturedOutput output) {
        kafkaTemplate.send("demo.events", "outside-pattern", "drop-me");

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> outgoing(e)
            && "outside-pattern".equals(JsonLogCapture.at(e, "kafka_info.key")));

        assertThat((String) JsonLogCapture.at(log, "kafka_info.value"))
            .as("filtering left off for this topic should keep the rejected record")
            .isEqualTo("drop-me");
    }

    private static boolean outgoing(Map<String, Object> event) {
        return "OUTGOING".equals(JsonLogCapture.at(event, "kafka_info.direction"));
    }

    @TestConfiguration
    static class FilterConfiguration {

        @Bean
        KafkaRecordLogFilter dropsMarkedRecords() {
            return record -> !"drop-me".equals(record.getValue());
        }
    }
}

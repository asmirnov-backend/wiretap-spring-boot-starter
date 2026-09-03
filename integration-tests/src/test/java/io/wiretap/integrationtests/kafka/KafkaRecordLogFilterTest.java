package io.wiretap.integrationtests.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import io.wiretap.kafka.message.KafkaRecordLogFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaRecordLogFilterTest extends WiretapIntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void consumerLogsOnlyRecordsAdmittedByTheFilterBean(CapturedOutput output) {
        kafkaTemplate.send("demo.events", "{\"requestSource\":\"system-2\"}", "filtered-out-value");
        kafkaTemplate.send("demo.events", "{\"requestSource\":\"system-1\"}", "admitted-value");

        JsonLogCapture.awaitMatching(output, e -> incoming(e)
            && "admitted-value".equals(JsonLogCapture.at(e, "kafka_info.value")));

        assertThat(JsonLogCapture.kafkaInfo(output).stream().filter(KafkaRecordLogFilterTest::incoming))
            .as("records rejected by the filter bean should never be logged")
            .noneMatch(e -> "filtered-out-value".equals(JsonLogCapture.at(e, "kafka_info.value")));
    }

    private static boolean incoming(Map<String, Object> event) {
        return "INCOMING".equals(JsonLogCapture.at(event, "kafka_info.direction"));
    }

    @TestConfiguration
    static class FilterConfiguration {

        @Bean
        KafkaRecordLogFilter requestSourceFilter() {
            final ObjectMapper mapper = new ObjectMapper();
            return record -> {
                try {
                    return "system-1".equals(mapper.readTree(record.getKey()).path("requestSource").asText());
                } catch (Exception e) {
                    return true;
                }
            };
        }
    }
}

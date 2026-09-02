package io.wiretap.integrationtests.kafka;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
    "wiretap.kafka-consumer-interceptor.key-json-include[/requestSource][0]=system-1"
})
class KafkaConsumerFilterTest extends WiretapIntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void consumerLogsOnlyRecordsAllowedByTheKeyFilter(CapturedOutput output) {
        kafkaTemplate.send("demo.events", "{\"requestSource\":\"system-2\"}", "filtered-out-value");
        kafkaTemplate.send("demo.events", "{\"requestSource\":\"system-1\"}", "admitted-value");

        JsonLogCapture.awaitMatching(output, e -> incoming(e)
            && "admitted-value".equals(JsonLogCapture.at(e, "kafka_info.value")));

        assertThat(JsonLogCapture.kafkaInfo(output).stream().filter(KafkaConsumerFilterTest::incoming))
            .as("records with a foreign requestSource should never be logged")
            .noneMatch(e -> "filtered-out-value".equals(JsonLogCapture.at(e, "kafka_info.value")));
    }

    private static boolean incoming(Map<String, Object> event) {
        return "INCOMING".equals(JsonLogCapture.at(event, "kafka_info.direction"));
    }
}

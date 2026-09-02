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

/**
 * Value masking is off globally and switched back on for one topic family. This is the
 * direction a "mask only these topics" configuration relies on, and the one the old
 * AND-based merge could not express.
 */
@TestPropertySource(properties = {
        "wiretap.kafka-producer-interceptor.enable-value-masking=false",
        "wiretap.kafka-producer-interceptor.specific-topic-settings[0].match-topic-pattern=secrets\\..*",
        "wiretap.kafka-producer-interceptor.specific-topic-settings[0].enable-value-masking=true"
})
class KafkaMaskingPerTopicTest extends WiretapIntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void perTopicOverrideMasksValue(CapturedOutput output) {
        kafkaTemplate.send("secrets.events", "per-topic-key", "super-secret-payload");

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"OUTGOING".equals(JsonLogCapture.at(e, "kafka_info.direction"))) return false;
            return "secrets.events".equals(JsonLogCapture.at(e, "kafka_info.topic"));
        });

        assertThat((String) JsonLogCapture.at(log, "kafka_info.value")).isEqualTo("***");
    }

    @Test
    void topicsOutsideThePatternKeepMaskingOff(CapturedOutput output) {
        kafkaTemplate.send("demo.events", "per-topic-key", "super-secret-payload");

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"OUTGOING".equals(JsonLogCapture.at(e, "kafka_info.direction"))) return false;
            if (!"demo.events".equals(JsonLogCapture.at(e, "kafka_info.topic"))) return false;
            return "per-topic-key".equals(JsonLogCapture.at(e, "kafka_info.key"));
        });

        assertThat((String) JsonLogCapture.at(log, "kafka_info.value")).isEqualTo("super-secret-payload");
    }
}

package io.wiretap.kafka.message.settings;

import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings.KafkaConfigurableField;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerLogMessageSettingsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaults_visibilityAllOn_logsEverything() {
        runner.run(ctx -> {
            KafkaConsumerLogMessageSettings props = ctx.getBean(KafkaConsumerLogMessageSettings.class);
            assertThat(props.getVisibilitySettings())
                    .containsEntry(KafkaConfigurableField.GROUP_ID, Boolean.TRUE)
                    .containsEntry(KafkaConfigurableField.OFFSET, Boolean.TRUE);
            assertThat(props.getExcludeTopicPatterns()).isEmpty();
        });
    }

    @Test
    void overrides_bindCleanly() {
        runner
                .withPropertyValues(
                        "wiretap.kafka-consumer-interceptor.visibility-settings.HEADERS=false",
                        "wiretap.kafka-consumer-interceptor.message-body-settings.max-value-length=128",
                        "wiretap.kafka-consumer-interceptor.exclude-topic-patterns[0]=__consumer_offsets"
                )
                .run(ctx -> {
                    KafkaConsumerLogMessageSettings props = ctx.getBean(KafkaConsumerLogMessageSettings.class);
                    assertThat(props.getVisibilitySettings().get(KafkaConfigurableField.HEADERS)).isFalse();
                    assertThat(props.getMessageBodySettings().getMaxValueLength()).isEqualTo(128);
                    assertThat(props.getExcludeTopicPatterns()).containsExactly("__consumer_offsets");
                });
    }

    @Test
    void keyJsonIncludeIsEmptyByDefault() {
        runner.run(ctx -> assertThat(ctx.getBean(KafkaConsumerLogMessageSettings.class).getKeyJsonInclude())
                .as("default settings should log every record")
                .isEmpty());
    }

    @Test
    void keyJsonIncludeBindsFromProperties() {
        runner
                .withPropertyValues(
                        "wiretap.kafka-consumer-interceptor.key-json-include[/requestSource][0]=system-1",
                        "wiretap.kafka-consumer-interceptor.key-json-include[/requestSource][1]=system-2"
                )
                .run(ctx -> assertThat(ctx.getBean(KafkaConsumerLogMessageSettings.class).getKeyJsonInclude())
                        .as("key-json-include should bind as pointer -> values")
                        .containsEntry("/requestSource", List.of("system-1", "system-2")));
    }

    @EnableConfigurationProperties(KafkaConsumerLogMessageSettings.class)
    static class TestConfig {
    }
}

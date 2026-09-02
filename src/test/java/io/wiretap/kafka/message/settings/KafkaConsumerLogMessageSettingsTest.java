package io.wiretap.kafka.message.settings;

import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings.KafkaConfigurableField;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
                    assertThat(props.getMessageBodySettings().getEffectiveMaxValueLength()).isEqualTo(128);
                    assertThat(props.getExcludeTopicPatterns()).containsExactly("__consumer_offsets");
                });
    }

    @Test
    void perTopicOverrideEnablesMaskingDisabledGlobally() {
        runner
                .withPropertyValues(
                        "wiretap.kafka-consumer-interceptor.enable-value-masking=false",
                        "wiretap.kafka-consumer-interceptor.specific-topic-settings[0].match-topic-pattern=secrets\\..*",
                        "wiretap.kafka-consumer-interceptor.specific-topic-settings[0].enable-value-masking=true"
                )
                .run(ctx -> {
                    KafkaConsumerLogMessageSettings props = ctx.getBean(KafkaConsumerLogMessageSettings.class);
                    assertThat(props.getSettingsByTopic("secrets.events").isValueMaskingEnabled()).isTrue();
                    assertThat(props.getSettingsByTopic("demo.events").isValueMaskingEnabled()).isFalse();
                });
    }

    @Test
    void perTopicOverrideOfOneBodySettingKeepsTheOthers() {
        runner
                .withPropertyValues(
                        "wiretap.kafka-consumer-interceptor.message-body-settings.enable-value-masking=true",
                        "wiretap.kafka-consumer-interceptor.message-body-settings.max-value-length=10000",
                        "wiretap.kafka-consumer-interceptor.specific-topic-settings[0].match-topic-pattern=bulk\\..*",
                        "wiretap.kafka-consumer-interceptor.specific-topic-settings[0].message-body-settings.max-value-length=50000"
                )
                .run(ctx -> {
                    KafkaConsumerLogMessageSettings props = ctx.getBean(KafkaConsumerLogMessageSettings.class);
                    var merged = props.getSettingsByTopic("bulk.events").getMessageBodySettings();
                    assertThat(merged.getEffectiveMaxValueLength()).isEqualTo(50000);
                    assertThat(merged.isValueMaskingEnabled()).isTrue();
                });
    }

    @Test
    void overrideCarriesOnlyTheKeysItConfigures() {
        runner
                .withPropertyValues(
                        "wiretap.kafka-consumer-interceptor.specific-topic-settings[0].match-topic-pattern=bulk\\..*",
                        "wiretap.kafka-consumer-interceptor.specific-topic-settings[0].visibility-settings.VALUE=false"
                )
                .run(ctx -> {
                    SpecificKafkaInfoLogMessageSettings override = ctx
                            .getBean(KafkaConsumerLogMessageSettings.class)
                            .getSpecificTopicSettings()
                            .get(0);

                    assertThat(override.getVisibilitySettings())
                            .containsExactly(java.util.Map.entry(KafkaConfigurableField.VALUE, Boolean.FALSE));
                    assertThat(override.getEnableValueMasking()).isNull();
                    assertThat(override.getHeaders()).isEmpty();
                });
    }

    @Test
    void topicBlockWithoutPatternIsIgnoredRatherThanThrowing() {
        runner
                .withPropertyValues(
                        "wiretap.kafka-consumer-interceptor.enable-value-masking=true",
                        "wiretap.kafka-consumer-interceptor.specific-topic-settings[0].enable-value-masking=false"
                )
                .run(ctx -> {
                    KafkaConsumerLogMessageSettings props = ctx.getBean(KafkaConsumerLogMessageSettings.class);
                    assertThat(props.getSettingsByTopic("demo.events").isValueMaskingEnabled()).isTrue();
                });
    }

    @EnableConfigurationProperties(KafkaConsumerLogMessageSettings.class)
    static class TestConfig {
    }
}

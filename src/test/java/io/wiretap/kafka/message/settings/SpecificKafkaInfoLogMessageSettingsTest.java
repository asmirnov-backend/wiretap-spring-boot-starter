package io.wiretap.kafka.message.settings;

import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings.KafkaConfigurableField;
import io.wiretap.kafka.message.settings.body.MessageBodySettings;
import io.wiretap.util.FieldVisibilityMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the per-topic override merge, mirroring
 * {@code SpecificHttpInfoLogMessageSettingsTest} on the HTTP side. An override has to work
 * in both directions — turning a setting off where it is globally on, and on where it is
 * globally off — and touching one nested value must not reset its siblings.
 */
class SpecificKafkaInfoLogMessageSettingsTest {

    @Test
    void overrideDisablesValueMasking_whenCommonEnablesIt() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.setEnableValueMasking(true);

        SpecificKafkaInfoLogMessageSettings override = override("orders\\..*");
        override.setEnableValueMasking(false);

        assertThat(override.getIntersectionSettings(common).isValueMaskingEnabled()).isFalse();
    }

    @Test
    void overrideEnablesValueMasking_whenCommonDisablesIt() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.setEnableValueMasking(false);

        SpecificKafkaInfoLogMessageSettings override = override("secrets\\..*");
        override.setEnableValueMasking(true);

        assertThat(override.getIntersectionSettings(common).isValueMaskingEnabled()).isTrue();
    }

    @Test
    void overrideEnablesHeadersMasking_whenCommonDisablesIt() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.setEnableHeadersMasking(false);

        SpecificKafkaInfoLogMessageSettings override = override("secrets\\..*");
        override.setEnableHeadersMasking(true);

        assertThat(override.getIntersectionSettings(common).isHeadersMaskingEnabled()).isTrue();
    }

    @Test
    void overrideEnablesTopicMasking_whenCommonDisablesIt() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.setEnableTopicMasking(false);

        SpecificKafkaInfoLogMessageSettings override = override("secrets\\..*");
        override.setEnableTopicMasking(true);

        assertThat(override.getIntersectionSettings(common).isTopicMaskingEnabled()).isTrue();
    }

    @Test
    void maskingTogglesFallBackToCommon_whenOverrideLeavesThemUnset() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.setEnableValueMasking(false);
        common.setEnableHeadersMasking(false);
        common.setEnableTopicMasking(false);

        KafkaInfoLogMessageSettings merged = override("orders\\..*").getIntersectionSettings(common);

        assertThat(merged.isValueMaskingEnabled()).isFalse();
        assertThat(merged.isHeadersMaskingEnabled()).isFalse();
        assertThat(merged.isTopicMaskingEnabled()).isFalse();
    }

    @Test
    void maskingTogglesDefaultToEnabled_whenNeitherSideConfiguresThem() {
        KafkaInfoLogMessageSettings merged =
                override(".*").getIntersectionSettings(new KafkaInfoLogMessageSettings());

        assertThat(merged.isValueMaskingEnabled()).isTrue();
        assertThat(merged.isHeadersMaskingEnabled()).isTrue();
        assertThat(merged.isTopicMaskingEnabled()).isTrue();
    }

    @Test
    void overridingOneBodySetting_keepsTheOtherCommonValues() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.getMessageBodySettings().setEnableValueMasking(true);
        common.getMessageBodySettings().setEnableValueTruncating(true);
        common.getMessageBodySettings().setMaxValueLength(10_000);

        SpecificKafkaInfoLogMessageSettings override = override("orders\\..*");
        override.getMessageBodySettings().setMaxValueLength(50_000);

        MessageBodySettings merged = override.getIntersectionSettings(common).getMessageBodySettings();
        assertThat(merged.getEffectiveMaxValueLength()).isEqualTo(50_000);
        assertThat(merged.isValueMaskingEnabled()).isTrue();
        assertThat(merged.isValueTruncatingEnabled()).isTrue();
    }

    @Test
    void overrideDisablesBodyValueMasking_whenCommonEnablesIt() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.getMessageBodySettings().setEnableValueMasking(true);

        SpecificKafkaInfoLogMessageSettings override = override("public\\..*");
        override.getMessageBodySettings().setEnableValueMasking(false);

        assertThat(override.getIntersectionSettings(common).getMessageBodySettings().isValueMaskingEnabled())
                .isFalse();
    }

    @Test
    void bodySettingsFallBackToCommon_whenOverrideConfiguresNone() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.getMessageBodySettings().setEnableValueMasking(true);
        common.getMessageBodySettings().setMaxValueLength(10_000);

        MessageBodySettings merged =
                override(".*").getIntersectionSettings(common).getMessageBodySettings();

        assertThat(merged.isValueMaskingEnabled()).isTrue();
        assertThat(merged.getEffectiveMaxValueLength()).isEqualTo(10_000);
    }

    @Test
    void overrideCanRevealAFieldHiddenGlobally() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.getVisibilitySettings().put(KafkaConfigurableField.VALUE, Boolean.FALSE);

        SpecificKafkaInfoLogMessageSettings override = override("orders\\..*");
        override.getVisibilitySettings().put(KafkaConfigurableField.VALUE, Boolean.TRUE);

        assertThat(override.getIntersectionSettings(common).getVisibilitySettings())
                .containsEntry(KafkaConfigurableField.VALUE, Boolean.TRUE);
    }

    @Test
    void overridingOneVisibilityKey_keepsTheOtherCommonValues() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.getVisibilitySettings().put(KafkaConfigurableField.HEADERS, Boolean.FALSE);

        SpecificKafkaInfoLogMessageSettings override = override("orders\\..*");
        override.getVisibilitySettings().put(KafkaConfigurableField.VALUE, Boolean.FALSE);

        FieldVisibilityMap<KafkaConfigurableField> merged =
                override.getIntersectionSettings(common).getVisibilitySettings();
        assertThat(merged)
                .containsEntry(KafkaConfigurableField.VALUE, Boolean.FALSE)
                .containsEntry(KafkaConfigurableField.HEADERS, Boolean.FALSE)
                .containsEntry(KafkaConfigurableField.TOPIC, Boolean.TRUE);
    }

    @Test
    void headersFallBackToCommon_whenOverrideLeavesThemEmpty() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.setHeaders(List.of("x-common"));

        assertThat(override(".*").getIntersectionSettings(common).getHeaders())
                .containsExactly("x-common");
    }

    @Test
    void headersFromOverrideWin_whenConfigured() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.setHeaders(List.of("x-common"));

        SpecificKafkaInfoLogMessageSettings override = override("orders\\..*");
        override.setHeaders(List.of("*"));

        assertThat(override.getIntersectionSettings(common).getHeaders()).containsExactly("*");
    }

    @Test
    void excludePatternsAreCarriedOverFromCommon() {
        KafkaInfoLogMessageSettings common = new KafkaInfoLogMessageSettings();
        common.setExcludeTopicPatterns(List.of("__consumer_offsets"));

        assertThat(override(".*").getIntersectionSettings(common).getExcludeTopicPatterns())
                .containsExactly("__consumer_offsets");
    }

    private static SpecificKafkaInfoLogMessageSettings override(String pattern) {
        SpecificKafkaInfoLogMessageSettings override = new SpecificKafkaInfoLogMessageSettings();
        override.setMatchTopicPattern(pattern);
        return override;
    }
}

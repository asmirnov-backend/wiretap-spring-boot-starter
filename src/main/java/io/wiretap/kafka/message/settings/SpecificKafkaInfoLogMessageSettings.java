package io.wiretap.kafka.message.settings;

import io.wiretap.kafka.message.settings.body.MessageBodySettings;
import io.wiretap.util.FieldVisibilityMap;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;

import java.util.Collections;

/**
 * Per-topic override of the common Kafka log settings.
 * Applied only when the topic name matches {@link #matchTopicPattern}.
 * <p>
 * Everything here starts out empty rather than at the library defaults, so the merge can
 * tell "the user configured this" apart from "this happens to equal the default". Without
 * that distinction an override could not switch a setting back on where it is globally
 * off, and touching one nested field would silently reset its siblings.
 */
public class SpecificKafkaInfoLogMessageSettings extends KafkaInfoLogMessageSettings {

    @Getter
    @Setter
    private String matchTopicPattern;

    public SpecificKafkaInfoLogMessageSettings() {
        // Spring binds collections and maps by merging into the existing instance, so
        // starting empty is what keeps unconfigured entries out of the override.
        setVisibilitySettings(new FieldVisibilityMap<>(KafkaConfigurableField.class));
        setHeaders(Collections.emptyList());
    }

    /**
     * Merges this override with the common settings field by field: anything configured
     * here wins, anything left unset falls back to the common settings.
     *
     * @param common common settings shared by all topics
     */
    public KafkaInfoLogMessageSettings getIntersectionSettings(KafkaInfoLogMessageSettings common) {
        final KafkaInfoLogMessageSettings merged = new KafkaInfoLogMessageSettings();

        merged.setMessageBodySettings(
                mergeBodySettings(this.getMessageBodySettings(), common.getMessageBodySettings())
        );
        merged.setHeaders(
                CollectionUtils.isEmpty(this.getHeaders()) ? common.getHeaders() : this.getHeaders()
        );
        merged.setVisibilitySettings(
                mergeVisibility(this.getVisibilitySettings(), common.getVisibilitySettings())
        );
        merged.setEnableValueMasking(
                this.getEnableValueMasking() != null
                        ? this.getEnableValueMasking() : common.getEnableValueMasking()
        );
        merged.setEnableHeadersMasking(
                this.getEnableHeadersMasking() != null
                        ? this.getEnableHeadersMasking() : common.getEnableHeadersMasking()
        );
        merged.setEnableTopicMasking(
                this.getEnableTopicMasking() != null
                        ? this.getEnableTopicMasking() : common.getEnableTopicMasking()
        );
        merged.setEnableRecordFiltering(
                this.getEnableRecordFiltering() != null
                        ? this.getEnableRecordFiltering() : common.getEnableRecordFiltering()
        );
        // Exclusion is a top-level decision made before the per-topic lookup, but carrying it
        // over keeps the merged object a faithful view of the effective settings.
        merged.setExcludeTopicPatterns(common.getExcludeTopicPatterns());

        return merged;
    }

    private static MessageBodySettings mergeBodySettings(MessageBodySettings override, MessageBodySettings common) {
        final MessageBodySettings merged = new MessageBodySettings();

        merged.setEnableValueMasking(override.getEnableValueMasking() != null
                ? override.getEnableValueMasking() : common.getEnableValueMasking());
        merged.setEnableValueTruncating(override.getEnableValueTruncating() != null
                ? override.getEnableValueTruncating() : common.getEnableValueTruncating());
        merged.setMaxValueLength(override.getMaxValueLength() != null
                ? override.getMaxValueLength() : common.getMaxValueLength());

        return merged;
    }

    /**
     * Keys configured on the override win; every other key keeps its common value.
     * The result carries the full key set because a missing key reads as "not visible".
     */
    private static FieldVisibilityMap<KafkaConfigurableField> mergeVisibility(
            FieldVisibilityMap<KafkaConfigurableField> override,
            FieldVisibilityMap<KafkaConfigurableField> common) {
        final FieldVisibilityMap<KafkaConfigurableField> merged = new FieldVisibilityMap<>(common);
        merged.putAll(override);
        return merged;
    }
}

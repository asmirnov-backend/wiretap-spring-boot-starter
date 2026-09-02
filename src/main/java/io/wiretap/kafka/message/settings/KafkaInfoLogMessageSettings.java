package io.wiretap.kafka.message.settings;

import io.wiretap.kafka.message.settings.body.MessageBodySettings;
import io.wiretap.util.FieldVisibilityMap;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Data
public class KafkaInfoLogMessageSettings {

    private MessageBodySettings messageBodySettings = new MessageBodySettings();

    /**
     * Toggles masking applied to {@code key} and {@code value}. Left {@code null} when not
     * configured, so a per-topic override can tell "inherit" apart from an explicit value.
     */
    private Boolean enableValueMasking;

    /** Toggles masking applied to logged record header values. See {@link #enableValueMasking}. */
    private Boolean enableHeadersMasking;

    /** Toggles masking applied to the logged topic name. See {@link #enableValueMasking}. */
    private Boolean enableTopicMasking;

    /** Record header names logged by default. */
    private Collection<String> headers = Arrays.asList("x-trace-id", "x-request-id");

    /** Topic patterns to skip from logging entirely. */
    private List<String> excludeTopicPatterns = Collections.emptyList();

    private FieldVisibilityMap<KafkaConfigurableField> visibilitySettings = getDefaultLogSettings();

    private List<SpecificKafkaInfoLogMessageSettings> specificTopicSettings = Collections.emptyList();

    /** Effective key/value masking toggle; enabled unless explicitly turned off. */
    public boolean isValueMaskingEnabled() {
        return !Boolean.FALSE.equals(enableValueMasking);
    }

    /** Effective header masking toggle; enabled unless explicitly turned off. */
    public boolean isHeadersMaskingEnabled() {
        return !Boolean.FALSE.equals(enableHeadersMasking);
    }

    /** Effective topic masking toggle; enabled unless explicitly turned off. */
    public boolean isTopicMaskingEnabled() {
        return !Boolean.FALSE.equals(enableTopicMasking);
    }

    public enum KafkaConfigurableField {
        TOPIC,
        PARTITION,
        OFFSET,
        CLIENT_ID,
        GROUP_ID,
        KEY,
        VALUE,
        HEADERS,
        TIMESTAMP,
        DURATION,
        STATUS
    }

    private FieldVisibilityMap<KafkaConfigurableField> getDefaultLogSettings() {
        final FieldVisibilityMap<KafkaConfigurableField> defaults = new FieldVisibilityMap<>(KafkaConfigurableField.class);
        for (KafkaConfigurableField field : KafkaConfigurableField.values()) {
            defaults.put(field, Boolean.TRUE);
        }
        return defaults;
    }

    /**
     * Returns the effective settings for a given topic.
     * Picks the first matching per-topic override and merges it with the common
     * settings; if no override matches, common settings are returned unchanged.
     */
    public KafkaInfoLogMessageSettings getSettingsByTopic(String topic) {
        return specificTopicSettings.stream()
                // A block without a pattern matches nothing: skipping it keeps a typo in the
                // YAML from throwing on every single record.
                .filter(settings -> StringUtils.hasText(settings.getMatchTopicPattern()))
                .filter(settings -> topic.matches(settings.getMatchTopicPattern()))
                .findFirst()
                .map(settings -> settings.getIntersectionSettings(this))
                .orElse(this);
    }
}

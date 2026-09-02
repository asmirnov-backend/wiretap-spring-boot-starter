package io.wiretap.kafka.message.settings;

import io.wiretap.kafka.message.settings.body.MessageBodySettings;
import io.wiretap.util.FieldVisibilityMap;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class KafkaInfoLogMessageSettings {

    private MessageBodySettings messageBodySettings = new MessageBodySettings();

    /** Toggles masking applied to {@code key} and {@code value}. */
    private boolean enableValueMasking = true;

    /** Toggles masking applied to logged record header values. */
    private boolean enableHeadersMasking = true;

    /** Toggles masking applied to the logged topic name. */
    private boolean enableTopicMasking = true;

    /** Record header names logged by default. */
    private Collection<String> headers = Arrays.asList("x-trace-id", "x-request-id");

    /** Topic patterns to skip from logging entirely. */
    private List<String> excludeTopicPatterns = Collections.emptyList();

    /**
     * JSON Pointer into the record key mapped to the values (regex) that admit
     * the record to the log. Empty by default — the whole topic is logged.
     */
    private Map<String, List<String>> keyJsonInclude = Collections.emptyMap();

    /** Compiled twin of {@link #keyJsonInclude}, rebuilt whenever the raw map is bound. */
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private KeyJsonInclude keyInclude = new KeyJsonInclude(Collections.emptyMap());

    private FieldVisibilityMap<KafkaConfigurableField> visibilitySettings = getDefaultLogSettings();

    private List<SpecificKafkaInfoLogMessageSettings> specificTopicSettings = Collections.emptyList();

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

    public void setKeyJsonInclude(Map<String, List<String>> keyJsonInclude) {
        this.keyJsonInclude = keyJsonInclude == null ? Collections.emptyMap() : keyJsonInclude;
        this.keyInclude = new KeyJsonInclude(this.keyJsonInclude);
    }

    /**
     * Copies the filter of {@code source} — the raw map together with its
     * compiled twin — so a per-topic merge, which runs per record, never
     * recompiles pointers and patterns.
     */
    void inheritKeyJsonInclude(KafkaInfoLogMessageSettings source) {
        this.keyJsonInclude = source.getKeyJsonInclude();
        this.keyInclude = source.getKeyInclude();
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
                .filter(settings -> topic.matches(settings.getMatchTopicPattern()))
                .findFirst()
                .map(settings -> settings.getIntersectionSettings(this))
                .orElse(this);
    }
}

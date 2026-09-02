package io.wiretap.kafka.message.settings;

import com.fasterxml.jackson.core.JsonPointer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        this.keyInclude = compile(this.keyJsonInclude);
    }

    private KeyJsonInclude compile(Map<String, List<String>> raw) {
        final Map<JsonPointer, List<Pattern>> compiled = new LinkedHashMap<>(raw.size());
        for (Map.Entry<String, List<String>> condition : raw.entrySet()) {
            compiled.put(
                    JsonPointer.compile(condition.getKey()),
                    condition.getValue().stream().map(Pattern::compile).collect(Collectors.toList())
            );
        }
        return new KeyJsonInclude(compiled);
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

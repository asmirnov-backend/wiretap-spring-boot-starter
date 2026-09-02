package io.wiretap.kafka.message.settings.body;

import lombok.Data;

/**
 * Truncation / masking knobs for Kafka message key and value rendered into the log.
 * Mirrors {@code HttpBodySettings} for HTTP traffic, including its nullable fields:
 * a per-topic override has to be able to tell "not configured" apart from a value that
 * happens to equal the default, otherwise it cannot switch a setting back on where it is
 * globally off. Read the effective values through the {@code isXxxEnabled()} /
 * {@code getEffectiveXxx()} accessors; the raw getters may return {@code null}.
 */
@Data
public class MessageBodySettings {

    public static final boolean DEFAULT_ENABLE_VALUE_MASKING = false;
    public static final boolean DEFAULT_ENABLE_VALUE_TRUNCATING = false;
    public static final int DEFAULT_MAX_VALUE_LENGTH = 2000;

    private Boolean enableValueMasking;
    private Boolean enableValueTruncating;
    private Integer maxValueLength;

    public boolean isValueMaskingEnabled() {
        return enableValueMasking == null ? DEFAULT_ENABLE_VALUE_MASKING : enableValueMasking;
    }

    public boolean isValueTruncatingEnabled() {
        return enableValueTruncating == null ? DEFAULT_ENABLE_VALUE_TRUNCATING : enableValueTruncating;
    }

    public int getEffectiveMaxValueLength() {
        return maxValueLength == null ? DEFAULT_MAX_VALUE_LENGTH : maxValueLength;
    }
}

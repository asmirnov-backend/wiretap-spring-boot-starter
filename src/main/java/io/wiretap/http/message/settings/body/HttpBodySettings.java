package io.wiretap.http.message.settings.body;

import lombok.Data;

/**
 * Body capture limits and masking toggles.
 * <p>
 * Every field is nullable so that a per-URL override can tell "not configured" apart
 * from a value that happens to equal the default — without that distinction an override
 * cannot switch a setting back on where it is globally off. Read the effective values
 * through the {@code isXxxEnabled()} / {@code getEffectiveXxx()} accessors, which apply
 * the defaults; the raw getters may return {@code null}.
 */
@Data
public class HttpBodySettings {

    public static final boolean DEFAULT_ENABLE_BODY_MASKING = false;
    public static final boolean DEFAULT_ENABLE_BODY_TRUNCATING = false;
    public static final int DEFAULT_MAX_FIELD_LENGTH = 1000;
    public static final int DEFAULT_MAX_BODY_LENGTH = 2000;

    private Boolean enableBodyMasking;
    private Boolean enableBodyTruncating;
    private Integer maxFieldLength;
    private Integer maxBodyLength;

    public boolean isBodyMaskingEnabled() {
        return enableBodyMasking == null ? DEFAULT_ENABLE_BODY_MASKING : enableBodyMasking;
    }

    public boolean isBodyTruncatingEnabled() {
        return enableBodyTruncating == null ? DEFAULT_ENABLE_BODY_TRUNCATING : enableBodyTruncating;
    }

    public int getEffectiveMaxFieldLength() {
        return maxFieldLength == null ? DEFAULT_MAX_FIELD_LENGTH : maxFieldLength;
    }

    public int getEffectiveMaxBodyLength() {
        return maxBodyLength == null ? DEFAULT_MAX_BODY_LENGTH : maxBodyLength;
    }
}

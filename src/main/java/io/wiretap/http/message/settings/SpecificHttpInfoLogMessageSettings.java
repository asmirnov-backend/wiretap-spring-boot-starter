package io.wiretap.http.message.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;
import io.wiretap.http.message.settings.body.HttpBodySettings;
import io.wiretap.util.FieldVisibilityMap;

import java.util.Collections;

/**
 * Per-URL override of the common log settings.
 * Applied only when the request URL matches {@link #matchUrlPattern}.
 * <p>
 * Everything here starts out empty rather than at the library defaults, so the merge can
 * tell "the user configured this" apart from "this happens to equal the default". Without
 * that distinction an override could not switch a setting back on where it is globally
 * off, and touching one nested field would silently reset its siblings.
 */
public class SpecificHttpInfoLogMessageSettings extends HttpInfoLogMessageSettings {

    @Getter
    @Setter
    private String matchUrlPattern;

    public SpecificHttpInfoLogMessageSettings() {
        // Spring binds collections and maps by merging into the existing instance, so
        // starting empty is what keeps unconfigured entries out of the override.
        setVisibilitySettings(new FieldVisibilityMap<>(HttpConfigurableField.class));
        setRequestHeaders(Collections.emptyList());
        setResponseHeaders(Collections.emptyList());
    }

    /**
     * Merges this override with the common settings field by field: anything configured
     * here wins, anything left unset falls back to the common settings.
     *
     * @param commonHttpInfoLogSettings common settings shared by all requests
     */
    public HttpInfoLogMessageSettings getIntersectionSettings(HttpInfoLogMessageSettings commonHttpInfoLogSettings) {
        final HttpInfoLogMessageSettings interSectionSettings = new HttpInfoLogMessageSettings();

        interSectionSettings.setHttpBodySettings(
                mergeBodySettings(this.getHttpBodySettings(), commonHttpInfoLogSettings.getHttpBodySettings())
        );

        interSectionSettings.setRequestHeaders(
                CollectionUtils.isEmpty(this.getRequestHeaders())
                        ? commonHttpInfoLogSettings.getRequestHeaders() : this.getRequestHeaders()
        );

        interSectionSettings.setResponseHeaders(
                CollectionUtils.isEmpty(this.getResponseHeaders())
                        ? commonHttpInfoLogSettings.getResponseHeaders() : this.getResponseHeaders()
        );

        interSectionSettings.setVisibilitySettings(
                mergeVisibility(this.getVisibilitySettings(), commonHttpInfoLogSettings.getVisibilitySettings())
        );

        interSectionSettings.setEnableUrlMasking(
                this.getEnableUrlMasking() != null
                        ? this.getEnableUrlMasking() : commonHttpInfoLogSettings.getEnableUrlMasking()
        );

        interSectionSettings.setEnableRequestParamsMasking(
                this.getEnableRequestParamsMasking() != null
                        ? this.getEnableRequestParamsMasking() : commonHttpInfoLogSettings.getEnableRequestParamsMasking()
        );

        return interSectionSettings;
    }

    private static HttpBodySettings mergeBodySettings(HttpBodySettings override, HttpBodySettings common) {
        final HttpBodySettings merged = new HttpBodySettings();

        merged.setEnableBodyMasking(override.getEnableBodyMasking() != null
                ? override.getEnableBodyMasking() : common.getEnableBodyMasking());
        merged.setEnableBodyTruncating(override.getEnableBodyTruncating() != null
                ? override.getEnableBodyTruncating() : common.getEnableBodyTruncating());
        merged.setMaxFieldLength(override.getMaxFieldLength() != null
                ? override.getMaxFieldLength() : common.getMaxFieldLength());
        merged.setMaxBodyLength(override.getMaxBodyLength() != null
                ? override.getMaxBodyLength() : common.getMaxBodyLength());

        return merged;
    }

    /**
     * Keys configured on the override win; every other key keeps its common value.
     * The result carries the full key set because a missing key reads as "not visible".
     */
    private static FieldVisibilityMap<HttpConfigurableField> mergeVisibility(
            FieldVisibilityMap<HttpConfigurableField> override,
            FieldVisibilityMap<HttpConfigurableField> common) {
        final FieldVisibilityMap<HttpConfigurableField> merged = new FieldVisibilityMap<>(common);
        merged.putAll(override);
        return merged;
    }
}

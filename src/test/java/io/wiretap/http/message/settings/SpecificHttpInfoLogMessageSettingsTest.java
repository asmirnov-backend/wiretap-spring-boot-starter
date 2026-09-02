package io.wiretap.http.message.settings;

import io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField;
import io.wiretap.http.message.settings.body.HttpBodySettings;
import io.wiretap.util.FieldVisibilityMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the per-URL override merge. The masking toggles are the interesting part:
 * they are documented as per-URL controls, so an override has to be able to both
 * enable masking where the common settings disable it and disable it where the
 * common settings enable it.
 */
class SpecificHttpInfoLogMessageSettingsTest {

    @Test
    void overrideDisablesRequestParamsMasking_whenCommonEnablesIt() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(true);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/internal/.*");
        override.setEnableRequestParamsMasking(false);

        assertThat(override.getIntersectionSettings(common).isRequestParamsMaskingEnabled()).isFalse();
    }

    @Test
    void overrideEnablesRequestParamsMasking_whenCommonDisablesIt() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(false);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/payments/.*");
        override.setEnableRequestParamsMasking(true);

        assertThat(override.getIntersectionSettings(common).isRequestParamsMaskingEnabled()).isTrue();
    }

    @Test
    void requestParamsMaskingFallsBackToCommon_whenOverrideLeavesItUnset() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(false);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/anything/.*");

        assertThat(override.getIntersectionSettings(common).isRequestParamsMaskingEnabled()).isFalse();
    }

    @Test
    void overrideDisablesUrlMasking_whenCommonEnablesIt() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableUrlMasking(true);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/internal/.*");
        override.setEnableUrlMasking(false);

        assertThat(override.getIntersectionSettings(common).isUrlMaskingEnabled()).isFalse();
    }

    @Test
    void overrideEnablesUrlMasking_whenCommonDisablesIt() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableUrlMasking(false);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/payments/.*");
        override.setEnableUrlMasking(true);

        assertThat(override.getIntersectionSettings(common).isUrlMaskingEnabled()).isTrue();
    }

    @Test
    void urlMaskingFallsBackToCommon_whenOverrideLeavesItUnset() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableUrlMasking(false);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/anything/.*");

        assertThat(override.getIntersectionSettings(common).isUrlMaskingEnabled()).isFalse();
    }

    @Test
    void bothMaskingTogglesDefaultToEnabled_whenNeitherSideConfiguresThem() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*");

        HttpInfoLogMessageSettings merged = override.getIntersectionSettings(common);
        assertThat(merged.isUrlMaskingEnabled()).isTrue();
        assertThat(merged.isRequestParamsMaskingEnabled()).isTrue();
    }

    @Test
    void headersFallBackToCommon_whenOverrideLeavesThemEmpty() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setRequestHeaders(List.of("X-Common-Request"));
        common.setResponseHeaders(List.of("X-Common-Response"));

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*");
        override.setRequestHeaders(List.of());
        override.setResponseHeaders(List.of());

        HttpInfoLogMessageSettings merged = override.getIntersectionSettings(common);
        assertThat(merged.getRequestHeaders()).containsExactly("X-Common-Request");
        assertThat(merged.getResponseHeaders()).containsExactly("X-Common-Response");
    }

    @Test
    void headersFallBackToCommon_whenOverrideConfiguresNone() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setRequestHeaders(List.of("X-Common-Request"));
        common.setResponseHeaders(List.of("X-Common-Response"));

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*");

        HttpInfoLogMessageSettings merged = override.getIntersectionSettings(common);
        assertThat(merged.getRequestHeaders()).containsExactly("X-Common-Request");
        assertThat(merged.getResponseHeaders()).containsExactly("X-Common-Response");
    }

    @Test
    void headersFromOverrideWin_whenConfigured() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setRequestHeaders(List.of("X-Common-Request"));

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*");
        override.setRequestHeaders(List.of("X-Override-Request"));

        assertThat(override.getIntersectionSettings(common).getRequestHeaders())
                .containsExactly("X-Override-Request");
    }

    @Test
    void overrideDisablesBodyMasking_whenCommonEnablesIt() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.getHttpBodySettings().setEnableBodyMasking(true);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/health.*");
        override.getHttpBodySettings().setEnableBodyMasking(false);

        assertThat(override.getIntersectionSettings(common).getHttpBodySettings().isBodyMaskingEnabled())
                .isFalse();
    }

    @Test
    void overrideEnablesBodyMasking_whenCommonDisablesIt() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.getHttpBodySettings().setEnableBodyMasking(false);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/payments/.*");
        override.getHttpBodySettings().setEnableBodyMasking(true);

        assertThat(override.getIntersectionSettings(common).getHttpBodySettings().isBodyMaskingEnabled())
                .isTrue();
    }

    @Test
    void overridingOneBodySetting_keepsTheOtherCommonValues() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.getHttpBodySettings().setEnableBodyMasking(true);
        common.getHttpBodySettings().setEnableBodyTruncating(true);
        common.getHttpBodySettings().setMaxFieldLength(500);
        common.getHttpBodySettings().setMaxBodyLength(10_000);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/upload/.*");
        override.getHttpBodySettings().setMaxBodyLength(50_000);

        HttpBodySettings merged = override.getIntersectionSettings(common).getHttpBodySettings();
        assertThat(merged.getEffectiveMaxBodyLength()).isEqualTo(50_000);
        assertThat(merged.isBodyMaskingEnabled()).isTrue();
        assertThat(merged.isBodyTruncatingEnabled()).isTrue();
        assertThat(merged.getEffectiveMaxFieldLength()).isEqualTo(500);
    }

    @Test
    void bodySettingsFallBackToCommon_whenOverrideConfiguresNone() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.getHttpBodySettings().setEnableBodyMasking(true);
        common.getHttpBodySettings().setMaxBodyLength(10_000);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*");

        HttpBodySettings merged = override.getIntersectionSettings(common).getHttpBodySettings();
        assertThat(merged.isBodyMaskingEnabled()).isTrue();
        assertThat(merged.getEffectiveMaxBodyLength()).isEqualTo(10_000);
    }

    /** The example documented in README.md — globally hide request bodies, show them on one endpoint. */
    @Test
    void overrideCanRevealAFieldHiddenGlobally() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.getVisibilitySettings().put(HttpConfigurableField.REQUEST_BODY, Boolean.FALSE);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/orders/.*");
        override.getVisibilitySettings().put(HttpConfigurableField.REQUEST_BODY, Boolean.TRUE);

        assertThat(override.getIntersectionSettings(common).getVisibilitySettings())
                .containsEntry(HttpConfigurableField.REQUEST_BODY, Boolean.TRUE);
    }

    @Test
    void overridingOneVisibilityKey_keepsTheOtherCommonValues() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.getVisibilitySettings().put(HttpConfigurableField.RESPONSE_HEADERS, Boolean.FALSE);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*/orders/.*");
        override.getVisibilitySettings().put(HttpConfigurableField.REQUEST_BODY, Boolean.FALSE);

        FieldVisibilityMap<HttpConfigurableField> merged =
                override.getIntersectionSettings(common).getVisibilitySettings();
        assertThat(merged)
                .containsEntry(HttpConfigurableField.REQUEST_BODY, Boolean.FALSE)
                .containsEntry(HttpConfigurableField.RESPONSE_HEADERS, Boolean.FALSE)
                .containsEntry(HttpConfigurableField.REQUEST_URL, Boolean.TRUE);
    }

    @Test
    void visibilityFallsBackToCommon_whenOverrideConfiguresNone() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.getVisibilitySettings().put(HttpConfigurableField.RESPONSE_BODY, Boolean.FALSE);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*");

        assertThat(override.getIntersectionSettings(common).getVisibilitySettings())
                .containsEntry(HttpConfigurableField.RESPONSE_BODY, Boolean.FALSE)
                .containsEntry(HttpConfigurableField.REQUEST_BODY, Boolean.TRUE);
    }

    @Test
    void visibilityFromOverrideWins_whenItDiffersFromTheDefault() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();

        FieldVisibilityMap<HttpConfigurableField> hidden = new FieldVisibilityMap<>(HttpConfigurableField.class);
        hidden.put(HttpConfigurableField.REQUEST_BODY, Boolean.FALSE);

        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(".*");
        override.setVisibilitySettings(hidden);

        assertThat(override.getIntersectionSettings(common).getVisibilitySettings())
                .containsEntry(HttpConfigurableField.REQUEST_BODY, Boolean.FALSE);
    }
}

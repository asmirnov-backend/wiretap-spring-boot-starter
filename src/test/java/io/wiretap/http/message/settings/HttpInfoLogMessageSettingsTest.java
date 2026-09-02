package io.wiretap.http.message.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers how a request URL is resolved to its effective settings. Patterns go through
 * {@link String#matches(String)}, so they have to match the whole URL — a substring
 * pattern silently matches nothing, which is easy to get wrong in YAML.
 */
class HttpInfoLogMessageSettingsTest {

    @Test
    void commonSettingsAreReturnedAsIs_whenNoOverrideMatches() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(false);
        common.setSpecificHttpInfoSettings(List.of(override(".*/other/.*", true)));

        HttpInfoLogMessageSettings resolved = common.getRequestSettingsByUrl("/api/echo");

        assertThat(resolved).isSameAs(common);
        assertThat(resolved.isRequestParamsMaskingEnabled()).isFalse();
    }

    @Test
    void matchingOverrideIsApplied() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(true);
        common.setSpecificHttpInfoSettings(List.of(override(".*/internal/.*", false)));

        HttpInfoLogMessageSettings resolved = common.getRequestSettingsByUrl("/api/internal/dump");

        assertThat(resolved).isNotSameAs(common);
        assertThat(resolved.isRequestParamsMaskingEnabled()).isFalse();
    }

    @Test
    void firstMatchingOverrideWins() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(true);
        common.setSpecificHttpInfoSettings(List.of(
                override(".*/internal/.*", false),
                override(".*", true)
        ));

        assertThat(common.getRequestSettingsByUrl("/api/internal/dump").isRequestParamsMaskingEnabled())
                .isFalse();
    }

    @Test
    void patternMustMatchTheWholeUrl() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(true);
        common.setSpecificHttpInfoSettings(List.of(override("/internal/", false)));

        // A bare substring never matches — String.matches is anchored.
        assertThat(common.getRequestSettingsByUrl("/api/internal/dump").isRequestParamsMaskingEnabled())
                .isTrue();
    }

    @Test
    void outgoingAbsoluteUrlsMatchTheSamePatterns() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableUrlMasking(true);
        common.setSpecificHttpInfoSettings(List.of(overrideUrlMasking(".*/internal/.*", false)));

        assertThat(common.getRequestSettingsByUrl("http://localhost:8080/api/internal/dump?x=1")
                .isUrlMaskingEnabled()).isFalse();
    }

    @Test
    void overrideWithoutPatternIsIgnoredRatherThanThrowing() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(true);

        SpecificHttpInfoLogMessageSettings brokenOverride = new SpecificHttpInfoLogMessageSettings();
        brokenOverride.setEnableRequestParamsMasking(false);   // match-url-pattern forgotten in YAML
        common.setSpecificHttpInfoSettings(List.of(brokenOverride, override(".*/internal/.*", false)));

        assertThat(common.getRequestSettingsByUrl("/api/echo").isRequestParamsMaskingEnabled()).isTrue();
        assertThat(common.getRequestSettingsByUrl("/api/internal/dump").isRequestParamsMaskingEnabled()).isFalse();
    }

    @Test
    void overrideWithBlankPatternIsIgnored() {
        HttpInfoLogMessageSettings common = new HttpInfoLogMessageSettings();
        common.setEnableRequestParamsMasking(true);

        SpecificHttpInfoLogMessageSettings blank = new SpecificHttpInfoLogMessageSettings();
        blank.setMatchUrlPattern("   ");
        blank.setEnableRequestParamsMasking(false);
        common.setSpecificHttpInfoSettings(List.of(blank));

        assertThat(common.getRequestSettingsByUrl("/api/echo").isRequestParamsMaskingEnabled()).isTrue();
    }

    private static SpecificHttpInfoLogMessageSettings override(String pattern, boolean paramsMasking) {
        SpecificHttpInfoLogMessageSettings settings = new SpecificHttpInfoLogMessageSettings();
        settings.setMatchUrlPattern(pattern);
        settings.setEnableRequestParamsMasking(paramsMasking);
        return settings;
    }

    private static SpecificHttpInfoLogMessageSettings overrideUrlMasking(String pattern, boolean urlMasking) {
        SpecificHttpInfoLogMessageSettings settings = new SpecificHttpInfoLogMessageSettings();
        settings.setMatchUrlPattern(pattern);
        settings.setEnableUrlMasking(urlMasking);
        return settings;
    }
}

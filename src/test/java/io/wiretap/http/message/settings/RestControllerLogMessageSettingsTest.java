package io.wiretap.http.message.settings;

import io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField;
import io.wiretap.http.message.settings.body.HttpBodySettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding-level cover for the per-URL override. The merge relies on an override carrying
 * only what the user wrote, which in turn relies on how Spring binds nested maps and
 * objects — so it is worth pinning down against the real binder, not just POJOs.
 */
class RestControllerLogMessageSettingsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void unconfiguredBodySettingsStayNull_soOverridesCanInherit() {
        runner.run(ctx -> {
            HttpBodySettings body = ctx.getBean(RestControllerLogMessageSettings.class).getHttpBodySettings();
            assertThat(body.getEnableBodyMasking()).isNull();
            assertThat(body.getMaxBodyLength()).isNull();
            // ... but reading through the effective accessors still yields the documented defaults
            assertThat(body.isBodyMaskingEnabled()).isFalse();
            assertThat(body.getEffectiveMaxBodyLength()).isEqualTo(2000);
        });
    }

    @Test
    void overrideCarriesOnlyTheKeysItConfigures() {
        runner
                .withPropertyValues(
                        "wiretap.rest-controllers.specific-http-info-settings[0].match-url-pattern=.*/orders/.*",
                        "wiretap.rest-controllers.specific-http-info-settings[0].visibility-settings.REQUEST_BODY=true",
                        "wiretap.rest-controllers.specific-http-info-settings[0].http-body-settings.max-body-length=50000"
                )
                .run(ctx -> {
                    SpecificHttpInfoLogMessageSettings override = ctx
                            .getBean(RestControllerLogMessageSettings.class)
                            .getSpecificHttpInfoSettings()
                            .get(0);

                    assertThat(override.getVisibilitySettings())
                            .containsExactly(java.util.Map.entry(HttpConfigurableField.REQUEST_BODY, Boolean.TRUE));
                    assertThat(override.getHttpBodySettings().getMaxBodyLength()).isEqualTo(50000);
                    assertThat(override.getHttpBodySettings().getEnableBodyMasking()).isNull();
                });
    }

    /** End-to-end through the binder: the example documented in README.md. */
    @Test
    void perUrlOverrideRevealsAFieldHiddenGlobally() {
        runner
                .withPropertyValues(
                        "wiretap.rest-controllers.visibility-settings.REQUEST_BODY=false",
                        "wiretap.rest-controllers.specific-http-info-settings[0].match-url-pattern=.*/orders/.*",
                        "wiretap.rest-controllers.specific-http-info-settings[0].visibility-settings.REQUEST_BODY=true"
                )
                .run(ctx -> {
                    RestControllerLogMessageSettings props = ctx.getBean(RestControllerLogMessageSettings.class);

                    assertThat(props.getRequestSettingsByUrl("/api/orders/42").getVisibilitySettings())
                            .containsEntry(HttpConfigurableField.REQUEST_BODY, Boolean.TRUE);
                    assertThat(props.getRequestSettingsByUrl("/api/echo").getVisibilitySettings())
                            .containsEntry(HttpConfigurableField.REQUEST_BODY, Boolean.FALSE);
                });
    }

    @Test
    void perUrlOverrideOfOneBodySettingKeepsTheOthers() {
        runner
                .withPropertyValues(
                        "wiretap.rest-controllers.http-body-settings.enable-body-masking=true",
                        "wiretap.rest-controllers.http-body-settings.max-body-length=10000",
                        "wiretap.rest-controllers.specific-http-info-settings[0].match-url-pattern=.*/upload/.*",
                        "wiretap.rest-controllers.specific-http-info-settings[0].http-body-settings.max-body-length=50000"
                )
                .run(ctx -> {
                    HttpBodySettings merged = ctx.getBean(RestControllerLogMessageSettings.class)
                            .getRequestSettingsByUrl("/api/upload/file")
                            .getHttpBodySettings();

                    assertThat(merged.getEffectiveMaxBodyLength()).isEqualTo(50000);
                    assertThat(merged.isBodyMaskingEnabled()).isTrue();
                });
    }

    @Test
    void perUrlOverrideCanDisableBodyMaskingEnabledGlobally() {
        runner
                .withPropertyValues(
                        "wiretap.rest-controllers.http-body-settings.enable-body-masking=true",
                        "wiretap.rest-controllers.specific-http-info-settings[0].match-url-pattern=.*/health.*",
                        "wiretap.rest-controllers.specific-http-info-settings[0].http-body-settings.enable-body-masking=false"
                )
                .run(ctx -> {
                    RestControllerLogMessageSettings props = ctx.getBean(RestControllerLogMessageSettings.class);

                    assertThat(props.getRequestSettingsByUrl("/api/health/live")
                            .getHttpBodySettings().isBodyMaskingEnabled()).isFalse();
                    assertThat(props.getRequestSettingsByUrl("/api/echo")
                            .getHttpBodySettings().isBodyMaskingEnabled()).isTrue();
                });
    }

    @EnableConfigurationProperties(RestControllerLogMessageSettings.class)
    static class TestConfig {
    }
}

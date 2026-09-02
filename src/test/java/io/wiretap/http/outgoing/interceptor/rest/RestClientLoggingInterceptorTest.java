package io.wiretap.http.outgoing.interceptor.rest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField;
import io.wiretap.http.message.settings.RestClientLogMessageSettings;
import io.wiretap.http.message.settings.SpecificHttpInfoLogMessageSettings;
import io.wiretap.http.message.settings.body.DefaultBodyParser;
import io.wiretap.metrics.NoOpWiretapMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the masking toggles are read from the settings resolved for the request
 * URL, not from the common block — the per-URL override is what the documentation
 * promises. Assertions run against the raw MDC payload so the test stays Jackson
 * agnostic and can be shared across every Spring Boot starter.
 */
class RestClientLoggingInterceptorTest {

    private static final String PHONE = "79991234567";

    private WireMockServer wireMock;
    private ListAppender<ILoggingEvent> appender;
    private Logger interceptorLogger;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse().withStatus(200)));

        interceptorLogger = (Logger) LoggerFactory.getLogger(RestLoggingInterceptor.class);
        appender = new ListAppender<>();
        appender.start();
        interceptorLogger.addAppender(appender);
        interceptorLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void stop() {
        interceptorLogger.detachAppender(appender);
        wireMock.stop();
    }

    @Test
    void perUrlOverrideEnablesParamsMasking_whenCommonDisablesIt() {
        RestClientLogMessageSettings settings = new RestClientLogMessageSettings();
        settings.setEnableRequestParamsMasking(false);
        settings.setSpecificHttpInfoSettings(List.of(paramsMaskingOverride(".*/search.*", true)));

        call(settings);

        assertThat(capturedHttpInfo()).contains("***").doesNotContain(PHONE);
    }

    @Test
    void perUrlOverrideDisablesParamsMasking_whenCommonEnablesIt() {
        RestClientLogMessageSettings settings = new RestClientLogMessageSettings();
        settings.setEnableRequestParamsMasking(true);
        settings.setSpecificHttpInfoSettings(List.of(paramsMaskingOverride(".*/search.*", false)));

        call(settings);

        assertThat(capturedHttpInfo()).contains(PHONE).doesNotContain("***");
    }

    @Test
    void commonParamsMaskingApplies_whenNoOverrideMatches() {
        RestClientLogMessageSettings settings = new RestClientLogMessageSettings();
        settings.setEnableRequestParamsMasking(true);
        settings.setSpecificHttpInfoSettings(List.of(paramsMaskingOverride(".*/other.*", false)));

        call(settings);

        assertThat(capturedHttpInfo()).contains("***").doesNotContain(PHONE);
    }

    @Test
    void perUrlOverrideDisablesUrlMasking_whenCommonEnablesIt() {
        RestClientLogMessageSettings settings = new RestClientLogMessageSettings();
        settings.setEnableUrlMasking(true);
        settings.setSpecificHttpInfoSettings(List.of(urlMaskingOverride(".*/search.*", false)));

        call(settings);

        assertThat(capturedHttpInfo()).contains("/search").doesNotContain("masked-url");
    }

    @Test
    void perUrlOverrideEnablesUrlMasking_whenCommonDisablesIt() {
        RestClientLogMessageSettings settings = new RestClientLogMessageSettings();
        settings.setEnableUrlMasking(false);
        settings.setSpecificHttpInfoSettings(List.of(urlMaskingOverride(".*/search.*", true)));

        call(settings);

        assertThat(capturedHttpInfo()).contains("masked-url");
    }

    @Test
    void urlMaskIsAppliedExactlyOnce() {
        RestClientLogMessageSettings settings = new RestClientLogMessageSettings();
        settings.setEnableUrlMasking(true);

        // Deliberately non-idempotent: a second pass would show up as a doubled suffix.
        call(settings, url -> url + "|masked");

        assertThat(capturedHttpInfo()).containsOnlyOnce("|masked");
        assertThat(loggedMessage()).containsOnlyOnce("|masked");
    }

    @Test
    void urlMaskingHandlerNeverSeesNull_whenRequestUrlIsHidden() {
        RestClientLogMessageSettings settings = new RestClientLogMessageSettings();
        settings.setEnableUrlMasking(true);
        settings.getVisibilitySettings().put(HttpConfigurableField.REQUEST_URL, Boolean.FALSE);

        List<String> seen = new ArrayList<>();
        call(settings, url -> {
            seen.add(url);
            return "masked-url";
        });

        assertThat(seen).doesNotContainNull();
    }

    private void call(RestClientLogMessageSettings settings) {
        call(settings, url -> "masked-url");
    }

    private void call(RestClientLogMessageSettings settings, HttpUrlMaskingHandler urlMaskingHandler) {
        RestClientLoggingInterceptor interceptor = new RestClientLoggingInterceptor(
                settings,
                new DefaultBodyParser(null),
                new HttpAccessFieldNames(),
                urlMaskingHandler,
                (name, value) -> "phone".equals(name) ? "***" : value,
                new NoOpWiretapMetrics());

        RestClient.builder()
                .requestInterceptor(interceptor)
                .build()
                .get()
                .uri(wireMock.baseUrl() + "/search?phone=" + PHONE)
                .retrieve()
                .toBodilessEntity();
    }

    private String loggedMessage() {
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0).getFormattedMessage();
    }

    private String capturedHttpInfo() {
        assertThat(appender.list)
                .as("interceptor should have emitted exactly one log event")
                .hasSize(1);
        String json = appender.list.get(0).getMDCPropertyMap().get("HTTP-REQUEST-LOG");
        assertThat(json).as("MDC entry HTTP-REQUEST-LOG should be present").isNotNull();
        return json;
    }

    private static SpecificHttpInfoLogMessageSettings paramsMaskingOverride(String pattern, boolean enabled) {
        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(pattern);
        override.setEnableRequestParamsMasking(enabled);
        return override;
    }

    private static SpecificHttpInfoLogMessageSettings urlMaskingOverride(String pattern, boolean enabled) {
        SpecificHttpInfoLogMessageSettings override = new SpecificHttpInfoLogMessageSettings();
        override.setMatchUrlPattern(pattern);
        override.setEnableUrlMasking(enabled);
        return override;
    }
}

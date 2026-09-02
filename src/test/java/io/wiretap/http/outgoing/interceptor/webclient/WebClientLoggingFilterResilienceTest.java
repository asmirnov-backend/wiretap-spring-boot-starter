package io.wiretap.http.outgoing.interceptor.webclient;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.SpecificHttpInfoLogMessageSettings;
import io.wiretap.http.message.settings.WebClientLogMessageSettings;
import io.wiretap.http.message.settings.body.DefaultBodyParser;
import io.wiretap.metrics.NoOpWiretapMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A broken logging configuration must never take the HTTP call down with it. The
 * WebClient filter resolves settings inside {@code filter(...)}, outside the try/catch
 * that guards the other interceptors, so a throw here propagates into the reactive
 * chain and fails the request itself rather than just the log entry.
 */
class WebClientLoggingFilterResilienceTest {

    private WireMockServer wireMock;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlPathEqualTo("/items/1"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    @Test
    void requestSucceeds_whenOverrideHasNoUrlPattern() {
        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        SpecificHttpInfoLogMessageSettings broken = new SpecificHttpInfoLogMessageSettings();
        broken.setEnableRequestParamsMasking(false);   // match-url-pattern forgotten
        settings.setSpecificHttpInfoSettings(List.of(broken));

        assertThat(callWith(settings)).isEqualTo("ok");
    }

    @Test
    void requestSucceeds_whenUrlPatternIsNotAValidRegex() {
        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        SpecificHttpInfoLogMessageSettings broken = new SpecificHttpInfoLogMessageSettings();
        broken.setMatchUrlPattern("[unclosed");
        settings.setSpecificHttpInfoSettings(List.of(broken));

        assertThat(callWith(settings)).isEqualTo("ok");
    }

    @Test
    void requestSucceeds_whenExcludePatternIsNotAValidRegex() {
        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        settings.setExcludeRequestPatterns(List.of("[unclosed"));

        assertThat(callWith(settings)).isEqualTo("ok");
    }

    private String callWith(WebClientLogMessageSettings settings) {
        WebClientLoggingFilter filter = new WebClientLoggingFilter(
                settings,
                new DefaultBodyParser(null),
                new HttpAccessFieldNames(),
                null,
                null,
                new NoOpWiretapMetrics());

        return WebClient.builder()
                .filter(filter)
                .build()
                .get()
                .uri(wireMock.baseUrl() + "/items/1")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}

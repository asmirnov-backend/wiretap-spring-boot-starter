package io.wiretap.integrationtests.http;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mirror of {@link HttpMaskingPerUrlDisabledTest}: masking is off globally and a
 * per-URL override switches it back on. This direction is the one a "mask only these
 * endpoints" configuration relies on.
 */
@TestPropertySource(properties = {
        "wiretap.rest-controllers.enable-request-params-masking=false",
        "wiretap.rest-controllers.specific-http-info-settings[0].match-url-pattern=.*/api/echo.*",
        "wiretap.rest-controllers.specific-http-info-settings[0].enable-request-params-masking=true"
})
class HttpMaskingPerUrlEnabledTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void perUrlOverrideMasksQueryParams(CapturedOutput output) {
        restTemplate.getForEntity(
            "/api/echo?marker=per-url-on&phone=79991234567",
            Map.class
        );

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) return false;
            Map<String, Object> params = JsonLogCapture.at(e, "http_info.request_params");
            return params != null && "per-url-on".equals(asFirst(params.get("marker")));
        });

        Map<String, Object> params = JsonLogCapture.at(log, "http_info.request_params");
        assertThat(asFirst(params.get("phone"))).isEqualTo("***");
    }

    @SuppressWarnings("unchecked")
    private static String asFirst(Object listOrString) {
        if (listOrString instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return listOrString == null ? null : listOrString.toString();
    }
}

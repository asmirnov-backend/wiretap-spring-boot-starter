package io.wiretap.integrationtests.http;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code exclude-request-patterns} defaults to {@code /actuator/.*}, so health checks
 * must not reach the access log at all — they are high volume and carry nothing useful.
 */
class HttpInboundExcludePatternsTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void actuatorRequestsAreNotLogged(CapturedOutput output) {
        restTemplate.getForEntity("/actuator/health", String.class);

        // Log the excluded request first, then a regular one: once the marker shows up we
        // know the access log has caught up and any actuator entry would already be there.
        restTemplate.getForEntity("/api/echo?marker=exclude-sync", Map.class);

        JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) return false;
            Map<String, Object> params = JsonLogCapture.at(e, "http_info.request_params");
            return params != null && "exclude-sync".equals(asFirst(params.get("marker")));
        });

        // Only inbound entries matter here: TestRestTemplate itself is instrumented, so the
        // same call also produces an OUTGOING entry governed by a separate exclude list.
        assertThat(JsonLogCapture.httpInfo(output))
                .filteredOn(log -> "INCOMING".equals(JsonLogCapture.at(log, "http_info.direction")))
                .extracting(log -> (String) JsonLogCapture.at(log, "http_info.request_url"))
                .noneMatch(url -> url != null && url.contains("/actuator"));
    }

    @SuppressWarnings("unchecked")
    private static String asFirst(Object listOrString) {
        if (listOrString instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return listOrString == null ? null : listOrString.toString();
    }
}

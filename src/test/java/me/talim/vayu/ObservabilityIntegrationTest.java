package me.talim.vayu;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import me.talim.vayu.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
// Spring Boot disables metric exporters by default in tests.
@AutoConfigureMetrics(export = true)
class ObservabilityIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesThePrometheusEndpoint() throws Exception {
        mockMvc
                .perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "vayu_worker_executor_queue_depth"
                )))
                .andExpect(content().string(containsString("application=\"vayu\"")));
    }

    @Test
    void exposesTheMetricsIndex() throws Exception {
        mockMvc
                .perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    void exposesTheHealthEndpoint() throws Exception {
        mockMvc
                .perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void returnsTheRequestIdHeader() throws Exception {
        mockMvc
                .perform(get("/endpoints")
                        .header("X-Request-Id", "test-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "test-request-123"));
    }
}

package me.talim.vayu.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesAValidRequestIdAndAddsRequestFieldsToMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/events");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "client-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> context = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> context.set(MDC.getCopyOfContextMap()));

        assertThat(context.get())
                .containsEntry(RequestIdFilter.REQUEST_ID_MDC_KEY, "client-request-123")
                .containsEntry("httpMethod", "POST")
                .containsEntry("httpPath", "/api/v1/events");
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo("client-request-123");
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)).isNull();
        assertThat(MDC.get("httpMethod")).isNull();
        assertThat(MDC.get("httpPath")).isNull();
    }

    @Test
    void generatesARequestIdWhenTheHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/endpoints");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> generated = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> generated.set(
                MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)
        ));

        assertThat(generated.get()).isNotBlank();
        UUID.fromString(generated.get());
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo(generated.get());
    }

    @Test
    void replacesAnInvalidRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/endpoints");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "not a valid id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> generated = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> generated.set(
                MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY)
        ));

        assertThat(generated.get()).isNotEqualTo("not a valid id");
        UUID.fromString(generated.get());
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo(generated.get());
    }
}

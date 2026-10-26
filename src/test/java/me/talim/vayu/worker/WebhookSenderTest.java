package me.talim.vayu.worker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;

class WebhookSenderTest {

    private WireMockServer server;
    private WebhookSender sender;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(300));

        sender = new WebhookSender(
                RestClient.builder().requestFactory(requestFactory).build(),
                new WebhookSigner()
        );
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void returnsDeliveredFor2xx() {
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        DeliveryOutcome outcome = sender.send(context(true), 1);

        assertThat(outcome.type()).isEqualTo(DeliveryOutcome.Type.DELIVERED);
        assertThat(outcome.httpStatus()).isEqualTo(200);
        assertThat(outcome.responseBody()).isEqualTo("ok");

        server.verify(1, postRequestedFor(urlEqualTo("/hook"))
                .withRequestBody(equalTo("{\"amount\":100}"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withHeader("X-Vayu-Event-Id", matching("[0-9a-f-]{36}"))
                .withHeader("X-Vayu-Event-Type", matching("payment.succeeded"))
                .withHeader("X-Vayu-Delivery-Id", matching("[0-9a-f-]{36}"))
                .withHeader("X-Vayu-Attempt", matching("1"))
                .withHeader("X-Vayu-Timestamp", matching("\\d+"))
                .withHeader("X-Vayu-Signature", matching("v1=[0-9a-f]{64}")));
    }

    @Test
    void retriesOnServerError() {
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        DeliveryOutcome outcome = sender.send(context(true), 1);

        assertThat(outcome.type()).isEqualTo(DeliveryOutcome.Type.RETRY);
        assertThat(outcome.httpStatus()).isEqualTo(500);
        assertThat(outcome.responseBody()).isEqualTo("boom");
        assertThat(outcome.error()).isEqualTo("receiver returned 500");
    }

    @Test
    void failsOnClientError() {
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(400).withBody("bad request")));

        DeliveryOutcome outcome = sender.send(context(true), 1);

        assertThat(outcome.type()).isEqualTo(DeliveryOutcome.Type.FAILED);
        assertThat(outcome.httpStatus()).isEqualTo(400);
        assertThat(outcome.responseBody()).isEqualTo("bad request");
    }

    @Test
    void failsOnRedirect() {
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(302)));

        DeliveryOutcome outcome = sender.send(context(true), 1);

        assertThat(outcome.type()).isEqualTo(DeliveryOutcome.Type.FAILED);
        assertThat(outcome.httpStatus()).isEqualTo(302);
    }

    @Test
    void retriesOnTimeout() {
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000)));

        DeliveryOutcome outcome = sender.send(context(true), 1);

        assertThat(outcome.type()).isEqualTo(DeliveryOutcome.Type.RETRY);
        assertThat(outcome.httpStatus()).isNull();
        assertThat(outcome.error()).isNotBlank();
    }

    @Test
    void honorsRetryAfter() {
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "7")));

        DeliveryOutcome outcome = sender.send(context(true), 1);

        assertThat(outcome.type()).isEqualTo(DeliveryOutcome.Type.RETRY);
        assertThat(outcome.retryAfter()).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void doesNotCallAnInactiveEndpoint() {
        DeliveryOutcome outcome = sender.send(context(false), 1);

        assertThat(outcome.type()).isEqualTo(DeliveryOutcome.Type.FAILED);
        assertThat(outcome.error()).isEqualTo("endpoint inactive");
        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void truncatesLargeResponseBodies() {
        String body = "x".repeat(2000);
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        DeliveryOutcome outcome = sender.send(context(true), 1);

        assertThat(outcome.responseBody()).hasSize(WebhookSender.MAX_RESPONSE_BODY);
    }

    private DeliveryContext context(boolean active) {
        return new DeliveryContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "payment.succeeded",
                "{\"amount\":100}",
                server.baseUrl() + "/hook",
                "secret",
                active
        );
    }
}

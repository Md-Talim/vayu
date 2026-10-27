package me.talim.vayu.worker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.github.tomakehurst.wiremock.WireMockServer;

import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryAttempt;
import me.talim.vayu.models.DeliveryStatus;
import me.talim.vayu.models.Endpoint;
import me.talim.vayu.models.Event;
import me.talim.vayu.models.Subscription;
import me.talim.vayu.repositories.DeliveryAttemptRepository;
import me.talim.vayu.repositories.DeliveryRepository;
import me.talim.vayu.repositories.EndpointRepository;
import me.talim.vayu.repositories.EventRepository;
import me.talim.vayu.repositories.SubscriptionRepository;
import me.talim.vayu.support.PostgresIntegrationTest;

@TestPropertySource(properties = "vayu.worker.read-timeout=500ms")
class DeliveryWorkerIntegrationTest extends PostgresIntegrationTest {

    private static final String TRUNCATE = """
            TRUNCATE delivery_attempts, deliveries, events, subscriptions, endpoints CASCADE
            """;

    @Autowired
    private DeliveryWorker worker;

    @Autowired
    private DeliveryClaimService claimService;

    @Autowired
    private DeliveryReaper reaper;

    @Autowired
    private DeliveryCompletionService completionService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryAttemptRepository attemptRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private WireMockServer server;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        jdbcTemplate.execute(TRUNCATE);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        server.stop();
        jdbcTemplate.execute(TRUNCATE);
    }

    @Test
    void deliversToTheEndpointAndRecordsTheAttempt() {
        UUID deliveryId = createDelivery(true);
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        assertThat(worker.runOnce()).isEqualTo(1);
        awaitStatus(deliveryId, DeliveryStatus.DELIVERED);

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getCompletedAt()).isNotNull();
        assertThat(delivery.getLockedAt()).isNull();
        assertThat(delivery.getLockToken()).isNull();

        DeliveryAttempt attempt = attempt(deliveryId, 1);
        assertThat(attempt.getHttpStatus()).isEqualTo(200);
        assertThat(attempt.getResponseBody()).isEqualTo("ok");
        assertThat(attempt.getError()).isNull();
        assertThat(attempt.getCompletedAt()).isNotNull();

        server.verify(1, postRequestedFor(urlEqualTo("/hook"))
                .withHeader("X-Vayu-Event-Id", matching("[0-9a-f-]{36}"))
                .withHeader("X-Vayu-Event-Type", matching("payment.succeeded"))
                .withHeader("X-Vayu-Delivery-Id", matching("[0-9a-f-]{36}"))
                .withHeader("X-Vayu-Attempt", matching("1"))
                .withHeader("X-Vayu-Timestamp", matching("\\d+"))
                .withHeader("X-Vayu-Signature", matching("v1=[0-9a-f]{64}")));

        var requests = server.findAll(postRequestedFor(urlEqualTo("/hook")));
        assertThat(requests).hasSize(1);

        var request = requests.get(0);
        String expectedSignature = "v1=" + new WebhookSigner().sign(
                "secret",
                request.getHeader("X-Vayu-Timestamp"),
                request.getBodyAsString()
        );

        assertThat(request.getHeader("X-Vayu-Signature")).isEqualTo(expectedSignature);
        assertThat(request.getBodyAsString()).contains("\"amount\"").doesNotStartWith("\"");
    }

    @Test
    void drainsMultipleBatchesPerPoll() {
        var deliveryIds = java.util.stream.IntStream.range(0, 30)
                .mapToObj(ignored -> createDelivery(true))
                .toList();
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(200)));

        assertThat(worker.runOnce()).isEqualTo(30);

        for (UUID deliveryId : deliveryIds) {
            awaitStatus(deliveryId, DeliveryStatus.DELIVERED);
        }

        server.verify(30, postRequestedFor(urlEqualTo("/hook")));
    }

    @Test
    void retriesAfterServerErrorAndDeliversOnTheNextAttempt() {
        UUID deliveryId = createDelivery(true);
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        Instant beforeFirstAttempt = Instant.now();
        assertThat(worker.runOnce()).isEqualTo(1);
        awaitStatus(deliveryId, DeliveryStatus.PENDING);

        Delivery afterFirstAttempt = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(afterFirstAttempt.getAttemptCount()).isEqualTo(1);
        assertThat(afterFirstAttempt.getNextAttemptAt()).isAfterOrEqualTo(beforeFirstAttempt);
        assertThat(afterFirstAttempt.getLockedAt()).isNull();
        assertThat(afterFirstAttempt.getLockToken()).isNull();
        assertThat(afterFirstAttempt.getCompletedAt()).isNull();

        DeliveryAttempt firstAttempt = attempt(deliveryId, 1);
        assertThat(firstAttempt.getHttpStatus()).isEqualTo(500);
        assertThat(firstAttempt.getError()).isEqualTo("receiver returned 500");
        assertThat(firstAttempt.getCompletedAt()).isNotNull();

        server.resetAll();
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(200)));
        jdbcTemplate.update(
                "UPDATE deliveries SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                deliveryId
        );

        assertThat(worker.runOnce()).isEqualTo(1);
        awaitStatus(deliveryId, DeliveryStatus.DELIVERED);

        Delivery afterSecondAttempt = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(afterSecondAttempt.getAttemptCount()).isEqualTo(2);
        assertThat(attempt(deliveryId, 2).getHttpStatus()).isEqualTo(200);
    }

    @Test
    void failsImmediatelyOnClientError() {
        UUID deliveryId = createDelivery(true);
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(400).withBody("bad request")));

        assertThat(worker.runOnce()).isEqualTo(1);
        awaitStatus(deliveryId, DeliveryStatus.FAILED);

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getCompletedAt()).isNotNull();
        assertThat(delivery.getLockedAt()).isNull();

        DeliveryAttempt attempt = attempt(deliveryId, 1);
        assertThat(attempt.getHttpStatus()).isEqualTo(400);
        assertThat(attempt.getError()).isEqualTo("receiver returned 400");
        assertThat(attempt.getCompletedAt()).isNotNull();
    }

    @Test
    void retriesWhenTheReceiverTimesOut() {
        UUID deliveryId = createDelivery(true);
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1500)));

        assertThat(worker.runOnce()).isEqualTo(1);
        awaitStatus(deliveryId, DeliveryStatus.PENDING);

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getCompletedAt()).isNull();

        DeliveryAttempt attempt = attempt(deliveryId, 1);
        assertThat(attempt.getHttpStatus()).isNull();
        assertThat(attempt.getError()).isNotBlank();
        assertThat(attempt.getCompletedAt()).isNotNull();
    }

    @Test
    void failsWhenAttemptsAreExhausted() {
        UUID deliveryId = createDelivery(true);
        jdbcTemplate.update(
                "UPDATE deliveries SET max_attempts = 1 WHERE id = ?",
                deliveryId
        );
        server.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(worker.runOnce()).isEqualTo(1);
        awaitStatus(deliveryId, DeliveryStatus.FAILED);

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getCompletedAt()).isNotNull();
        assertThat(attempt(deliveryId, 1).getHttpStatus()).isEqualTo(500);
    }

    @Test
    void failsWithoutCallingAnInactiveEndpoint() {
        UUID deliveryId = createDelivery(false);

        assertThat(worker.runOnce()).isEqualTo(1);
        awaitStatus(deliveryId, DeliveryStatus.FAILED);

        DeliveryAttempt attempt = attempt(deliveryId, 1);
        assertThat(attempt.getHttpStatus()).isNull();
        assertThat(attempt.getError()).isEqualTo("endpoint inactive");
        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void reaperReturnsAStaleDeliveryToPending() {
        UUID deliveryId = createDelivery(true);
        claimService.claim(1);
        jdbcTemplate.update(
                "UPDATE deliveries SET locked_at = now() - interval '10 minutes' WHERE id = ?",
                deliveryId
        );

        assertThat(reaper.runOnce()).isEqualTo(1);

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getLockedAt()).isNull();
        assertThat(delivery.getLockToken()).isNull();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNotNull();

        DeliveryAttempt attempt = attempt(deliveryId, 1);
        assertThat(attempt.getCompletedAt()).isNotNull();
        assertThat(attempt.getError()).isEqualTo("lease expired");
    }

    @Test
    void reaperFailsADeliveryWhenAttemptsAreExhausted() {
        UUID deliveryId = createDelivery(true);
        jdbcTemplate.update(
                "UPDATE deliveries SET max_attempts = 1 WHERE id = ?",
                deliveryId
        );
        claimService.claim(1);
        jdbcTemplate.update(
                "UPDATE deliveries SET locked_at = now() - interval '10 minutes' WHERE id = ?",
                deliveryId
        );

        assertThat(reaper.runOnce()).isEqualTo(1);

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getCompletedAt()).isNotNull();
        assertThat(delivery.getLockedAt()).isNull();
        assertThat(delivery.getLockToken()).isNull();
    }

    @Test
    void rejectsALateCompletionAfterTheLeaseWasReaped() {
        UUID deliveryId = createDelivery(true);
        ClaimedDelivery claim = claimService.claim(1).get(0);
        jdbcTemplate.update(
                "UPDATE deliveries SET locked_at = now() - interval '10 minutes' WHERE id = ?",
                deliveryId
        );
        assertThat(reaper.runOnce()).isEqualTo(1);

        boolean completed = completionService.complete(
                claim,
                DeliveryOutcome.delivered(200, "ok")
        );

        assertThat(completed).isFalse();

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getCompletedAt()).isNull();
        assertThat(delivery.getLockToken()).isNull();
        assertThat(attempt(deliveryId, 1).getError()).isEqualTo("lease expired");
    }

    private UUID createDelivery(boolean active) {
        UUID deliveryId = transactionTemplate.execute(status -> {
            Endpoint endpoint = new Endpoint(server.baseUrl() + "/hook", "secret");
            endpoint.setActive(active);
            Endpoint savedEndpoint = endpointRepository.save(endpoint);

            Subscription subscription = subscriptionRepository.save(
                    new Subscription(savedEndpoint, "payment.succeeded")
            );
            Event event = eventRepository.save(new Event(
                    "payment.succeeded",
                    "{\"amount\":100}",
                    "idem-" + UUID.randomUUID()
            ));
            Delivery delivery = deliveryRepository.save(new Delivery(event, subscription));
            return delivery.getId();
        });

        assertThat(deliveryId).isNotNull();
        return deliveryId;
    }

    private DeliveryAttempt attempt(UUID deliveryId, int attemptNumber) {
        return attemptRepository
                .findByDelivery_IdAndAttemptNumber(deliveryId, attemptNumber)
                .orElseThrow();
    }

    private void awaitStatus(UUID deliveryId, DeliveryStatus status) {
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> assertThat(
                        deliveryRepository.findById(deliveryId).orElseThrow().getStatus()
                ).isEqualTo(status));
    }
}

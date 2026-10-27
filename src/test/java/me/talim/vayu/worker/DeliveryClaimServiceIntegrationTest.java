package me.talim.vayu.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

class DeliveryClaimServiceIntegrationTest extends PostgresIntegrationTest {

    private static final String TRUNCATE = """
            TRUNCATE delivery_attempts, deliveries, events, subscriptions, endpoints CASCADE
            """;

    @Autowired
    private DeliveryClaimService claimService;

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

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(TRUNCATE);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute(TRUNCATE);
    }

    @Test
    void claimsOnlyDuePendingDeliveriesAndCreatesAnAttempt() {
        UUID due = createDelivery();
        UUID future = createDelivery();
        UUID delivered = createDelivery();
        UUID exhausted = createDelivery();

        jdbcTemplate.update(
                "UPDATE deliveries SET next_attempt_at = now() + interval '1 hour' WHERE id = ?",
                future
        );
        jdbcTemplate.update(
                "UPDATE deliveries SET status = 'DELIVERED', completed_at = now() WHERE id = ?",
                delivered
        );
        jdbcTemplate.update(
                "UPDATE deliveries SET attempt_count = max_attempts WHERE id = ?",
                exhausted
        );

        List<ClaimedDelivery> claimed = claimService.claim(10);

        assertThat(claimed).extracting(ClaimedDelivery::deliveryId).containsExactly(due);
        assertThat(claimed.get(0).attemptNumber()).isEqualTo(1);
        assertThat(claimed.get(0).maxAttempts()).isEqualTo(10);

        Delivery claimedDelivery = deliveryRepository.findById(due).orElseThrow();
        assertThat(claimedDelivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERING);
        assertThat(claimedDelivery.getAttemptCount()).isEqualTo(1);
        assertThat(claimedDelivery.getLockedAt()).isNotNull();
        assertThat(claimedDelivery.getLockToken()).isNotNull();
        assertThat(claimedDelivery.getCompletedAt()).isNull();
        assertThat(claimedDelivery.getLockToken()).isEqualTo(claimed.get(0).lockToken());

        Delivery pendingFuture = deliveryRepository.findById(future).orElseThrow();
        assertThat(pendingFuture.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(pendingFuture.getAttemptCount()).isZero();
        assertThat(pendingFuture.getLockedAt()).isNull();

        Delivery terminal = deliveryRepository.findById(delivered).orElseThrow();
        assertThat(terminal.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        Delivery maxed = deliveryRepository.findById(exhausted).orElseThrow();
        assertThat(maxed.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(maxed.getAttemptCount()).isEqualTo(maxed.getMaxAttempts());

        DeliveryAttempt attempt = attemptRepository
                .findByDelivery_IdAndAttemptNumber(due, 1)
                .orElseThrow();
        assertThat(attempt.getStartedAt()).isNotNull();
        assertThat(attempt.getCompletedAt()).isNull();
        assertThat(attempt.getHttpStatus()).isNull();
    }

    @Test
    void claimsAtMostTheBatchSize() {
        createDelivery();
        createDelivery();
        createDelivery();

        List<ClaimedDelivery> claimed = claimService.claim(2);

        assertThat(claimed).hasSize(2);
        assertThat(deliveryRepository.findAll())
                .filteredOn(delivery -> delivery.getStatus() == DeliveryStatus.DELIVERING)
                .hasSize(2);
        assertThat(deliveryRepository.findAll())
                .filteredOn(delivery -> delivery.getStatus() == DeliveryStatus.PENDING)
                .hasSize(1);
    }

    @Test
    void concurrentClaimsNeverReturnTheSameDelivery() throws Exception {
        UUID deliveryId = createDelivery();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<Integer> claim = () -> {
                start.await();
                return claimService.claim(1).size();
            };

            Future<Integer> first = executor.submit(claim);
            Future<Integer> second = executor.submit(claim);
            start.countDown();

            int total = first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS);
            assertThat(total).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERING);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(attemptRepository.findByDelivery_IdAndAttemptNumber(deliveryId, 1))
                .isPresent();
    }

    private UUID createDelivery() {
        UUID deliveryId = transactionTemplate.execute(status -> {
            Endpoint endpoint = endpointRepository.save(
                    new Endpoint("http://localhost:1/hook", "secret")
            );
            Subscription subscription = subscriptionRepository.save(
                    new Subscription(endpoint, "payment.succeeded")
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
}

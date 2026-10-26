package me.talim.vayu.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnitUtil;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryAttempt;
import me.talim.vayu.models.DeliveryStatus;
import me.talim.vayu.models.Endpoint;
import me.talim.vayu.models.Event;
import me.talim.vayu.models.Subscription;
import me.talim.vayu.support.PostgresIntegrationTest;

@Transactional
class DeliveryRepositoryIntegrationTest extends PostgresIntegrationTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(
            0,
            10,
            Sort.by(Sort.Direction.DESC, "createdAt")
    );

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void filtersByStatusAndEndpoint() {
        Fixture fixture = createFixture();
        Delivery pending = deliveryRepository.save(
                new Delivery(fixture.event(), fixture.firstSubscription())
        );
        Delivery delivered = deliveryRepository.save(
                new Delivery(fixture.event(), fixture.secondSubscription())
        );
        entityManager.flush();

        delivered.markDelivered(Instant.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(deliveryRepository.findPage(null, null, FIRST_PAGE).getTotalElements())
                .isEqualTo(2);

        assertThat(deliveryRepository.findPage(DeliveryStatus.PENDING, null, FIRST_PAGE)
                .getContent())
                .extracting(Delivery::getId)
                .containsExactly(pending.getId());

        assertThat(deliveryRepository.findPage(DeliveryStatus.DELIVERED, null, FIRST_PAGE)
                .getContent())
                .extracting(Delivery::getId)
                .containsExactly(delivered.getId());

        assertThat(deliveryRepository.findPage(null, fixture.firstEndpoint().getId(), FIRST_PAGE)
                .getContent())
                .extracting(Delivery::getId)
                .containsExactly(pending.getId());

        assertThat(deliveryRepository.findPage(
                DeliveryStatus.DELIVERED,
                fixture.firstEndpoint().getId(),
                FIRST_PAGE
        ).getContent()).isEmpty();
    }

    @Test
    void detailQueryLoadsEventEndpointAndAttempts() {
        Fixture fixture = createFixture();
        Delivery delivery = deliveryRepository.save(
                new Delivery(fixture.event(), fixture.firstSubscription())
        );
        Instant now = Instant.now();
        DeliveryAttempt firstAttempt = new DeliveryAttempt(delivery, 1, now);
        firstAttempt.complete(500, "receiver error", "receiver returned 500", now);
        DeliveryAttempt secondAttempt = new DeliveryAttempt(delivery, 2, now);
        secondAttempt.complete(204, null, null, now);
        entityManager.persist(firstAttempt);
        entityManager.persist(secondAttempt);
        entityManager.flush();
        entityManager.clear();

        Delivery detailed = deliveryRepository.findDetailedById(delivery.getId()).orElseThrow();

        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(detailed, "event")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(detailed, "subscription")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(detailed, "attempts")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(detailed.getSubscription(), "endpoint")).isTrue();

        assertThat(detailed.getAttempts()).hasSize(2);
        assertThat(detailed.getEvent().getType()).isEqualTo("payment.succeeded");
        assertThat(detailed.getSubscription().getEndpoint().getUrl())
                .isEqualTo("https://one.example.com/hook");
    }

    @Test
    void pageQueryLoadsEventAndEndpointWithoutLazyLoading() {
        Fixture fixture = createFixture();
        deliveryRepository.save(new Delivery(fixture.event(), fixture.firstSubscription()));
        entityManager.flush();
        entityManager.clear();

        Delivery listed = deliveryRepository.findPage(null, null, FIRST_PAGE)
                .getContent()
                .get(0);

        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(listed, "event")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(listed, "subscription")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(listed.getSubscription(), "endpoint")).isTrue();
    }

    private Fixture createFixture() {
        Event event = eventRepository.save(
                new Event("payment.succeeded", "{\"amount\":100}", "idem-" + UUID.randomUUID())
        );
        Endpoint firstEndpoint = endpointRepository.save(
                new Endpoint("https://one.example.com/hook", "secret-1")
        );
        Endpoint secondEndpoint = endpointRepository.save(
                new Endpoint("https://two.example.com/hook", "secret-2")
        );
        Subscription firstSubscription = subscriptionRepository.save(
                new Subscription(firstEndpoint, "payment.succeeded")
        );
        Subscription secondSubscription = subscriptionRepository.save(
                new Subscription(secondEndpoint, "payment.succeeded")
        );

        return new Fixture(event, firstEndpoint, secondEndpoint, firstSubscription, secondSubscription);
    }

    private record Fixture(
            Event event,
            Endpoint firstEndpoint,
            Endpoint secondEndpoint,
            Subscription firstSubscription,
            Subscription secondSubscription
    ) {}
}

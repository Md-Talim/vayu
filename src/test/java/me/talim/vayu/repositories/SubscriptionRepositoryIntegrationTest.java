package me.talim.vayu.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.models.Endpoint;
import me.talim.vayu.models.Subscription;
import me.talim.vayu.support.PostgresIntegrationTest;

@Transactional
class SubscriptionRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Test
    void returnsOnlySubscriptionsOfActiveEndpoints() {
        Endpoint active = endpointRepository.save(
                new Endpoint("https://active.example.com/hook", "secret-1")
        );
        Endpoint inactive = endpointRepository.save(
                new Endpoint("https://inactive.example.com/hook", "secret-2")
        );
        inactive.setActive(false);
        endpointRepository.save(inactive);

        subscriptionRepository.save(new Subscription(active, "payment.succeeded"));
        subscriptionRepository.save(new Subscription(inactive, "payment.succeeded"));
        subscriptionRepository.save(new Subscription(active, "order.created"));

        List<Subscription> subscriptions = subscriptionRepository
                .findAllByEventTypeAndEndpoint_ActiveTrue("payment.succeeded");

        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).getEndpoint().getId()).isEqualTo(active.getId());
        assertThat(subscriptions.get(0).getEndpoint().isActive()).isTrue();
    }

    @Test
    void returnsNothingWhenAllMatchingEndpointsAreInactive() {
        Endpoint inactive = endpointRepository.save(
                new Endpoint("https://inactive.example.com/hook", "secret-1")
        );
        inactive.setActive(false);
        endpointRepository.save(inactive);

        subscriptionRepository.save(new Subscription(inactive, "payment.succeeded"));

        assertThat(subscriptionRepository.findAllByEventTypeAndEndpoint_ActiveTrue(
                "payment.succeeded"
        )).isEmpty();
    }
}

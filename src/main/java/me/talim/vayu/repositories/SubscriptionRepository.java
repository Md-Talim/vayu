package me.talim.vayu.repositories;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import me.talim.vayu.models.Endpoint;
import me.talim.vayu.models.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    public List<Subscription> findAllByEndpoint(Endpoint endpoint);

    public List<Subscription> findAllByEventTypeAndEndpoint_ActiveTrue(String eventType);

    public List<Subscription> findAllByEndpoint_IdIn(Collection<UUID> endpointIds);
}

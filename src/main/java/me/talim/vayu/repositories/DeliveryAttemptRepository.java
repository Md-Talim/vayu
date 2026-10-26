package me.talim.vayu.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import me.talim.vayu.models.DeliveryAttempt;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    Optional<DeliveryAttempt> findByDelivery_IdAndAttemptNumber(UUID deliveryId, int attemptNumber);
}

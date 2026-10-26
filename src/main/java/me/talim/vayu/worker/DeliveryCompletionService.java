package me.talim.vayu.worker;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryAttempt;
import me.talim.vayu.models.DeliveryStatus;
import me.talim.vayu.repositories.DeliveryAttemptRepository;
import me.talim.vayu.repositories.DeliveryRepository;

@Service
public class DeliveryCompletionService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCompletionService.class);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final RetryPolicy retryPolicy;

    public DeliveryCompletionService(
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository attemptRepository,
            RetryPolicy retryPolicy
    ) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public boolean complete(ClaimedDelivery claim, DeliveryOutcome outcome) {
        Delivery delivery = deliveryRepository.findByIdForUpdate(claim.deliveryId()).orElse(null);

        if (delivery == null
                || delivery.getStatus() != DeliveryStatus.DELIVERING
                || !claim.lockToken().equals(delivery.getLockToken())) {
            return false;
        }

        Instant now = Instant.now();

        switch (outcome.type()) {
            case DELIVERED -> delivery.markDelivered(now);
            case RETRY -> completeRetry(delivery, claim.attemptNumber(), outcome, now);
            case FAILED -> delivery.markFailed(now);
        }

        DeliveryAttempt attempt = attemptRepository
                .findByDelivery_IdAndAttemptNumber(claim.deliveryId(), claim.attemptNumber())
                .orElse(null);

        if (attempt == null) {
            log.warn(
                    "Delivery {} is missing attempt {}",
                    claim.deliveryId(),
                    claim.attemptNumber()
            );
        } else {
            attempt.complete(
                    outcome.httpStatus(),
                    outcome.responseBody(),
                    outcome.error(),
                    now
            );
        }

        return true;
    }

    private void completeRetry(
            Delivery delivery,
            int attemptNumber,
            DeliveryOutcome outcome,
            Instant now
    ) {
        if (attemptNumber >= delivery.getMaxAttempts()) {
            delivery.markFailed(now);
            return;
        }

        Duration delay = retryPolicy.nextDelay(attemptNumber, outcome.retryAfter());
        delivery.scheduleRetry(now.plus(delay), now);
    }
}

package me.talim.vayu.worker;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import me.talim.vayu.models.DeliveryStatus;

@Service
public class DeliveryCompletionService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCompletionService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final RetryPolicy retryPolicy;

    public DeliveryCompletionService(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public boolean complete(ClaimedDelivery claim, DeliveryOutcome outcome) {
        Instant now = Instant.now();
        DeliveryStatus nextStatus = nextStatus(claim, outcome);
        Instant nextAttemptAt = nextStatus == DeliveryStatus.PENDING
                ? now.plus(retryDelay(claim, outcome))
                : null;

        // The status and lock token in the WHERE clause are the lease fence.
        // If the reaper changed either value, the update matches no row.
        int updated = nextStatus == DeliveryStatus.PENDING
                ? completeAsPending(claim, nextAttemptAt, now)
                : completeAsTerminal(claim, nextStatus, now);

        if (updated == 0) {
            return false;
        }

        int attemptUpdated = entityManager.createNativeQuery("""
                UPDATE delivery_attempts
                SET http_status = :httpStatus,
                    response_body = :responseBody,
                    error = :error,
                    completed_at = :now
                WHERE delivery_id = :deliveryId
                  AND attempt_number = :attemptNumber
                """)
                .setParameter("httpStatus", outcome.httpStatus())
                .setParameter("responseBody", outcome.responseBody())
                .setParameter("error", outcome.error())
                .setParameter("now", now)
                .setParameter("deliveryId", claim.deliveryId())
                .setParameter("attemptNumber", claim.attemptNumber())
                .executeUpdate();

        if (attemptUpdated == 0) {
            log.warn(
                    "Delivery {} is missing attempt {}",
                    claim.deliveryId(),
                    claim.attemptNumber()
            );
        }

        return true;
    }

    private DeliveryStatus nextStatus(ClaimedDelivery claim, DeliveryOutcome outcome) {
        return switch (outcome.type()) {
            case DELIVERED -> DeliveryStatus.DELIVERED;
            case FAILED -> DeliveryStatus.FAILED;
            case RETRY -> claim.attemptNumber() >= claim.maxAttempts()
                    ? DeliveryStatus.FAILED
                    : DeliveryStatus.PENDING;
        };
    }

    private Duration retryDelay(ClaimedDelivery claim, DeliveryOutcome outcome) {
        return retryPolicy.nextDelay(claim.attemptNumber(), outcome.retryAfter());
    }

    private int completeAsPending(
            ClaimedDelivery claim,
            Instant nextAttemptAt,
            Instant now
    ) {
        return entityManager.createNativeQuery("""
                UPDATE deliveries
                SET status = 'PENDING',
                    next_attempt_at = :nextAttemptAt,
                    completed_at = NULL,
                    locked_at = NULL,
                    lock_token = NULL,
                    updated_at = :now
                WHERE id = :deliveryId
                  AND status = 'DELIVERING'
                  AND lock_token = :lockToken
                """)
                .setParameter("nextAttemptAt", nextAttemptAt)
                .setParameter("now", now)
                .setParameter("deliveryId", claim.deliveryId())
                .setParameter("lockToken", claim.lockToken())
                .executeUpdate();
    }

    private int completeAsTerminal(
            ClaimedDelivery claim,
            DeliveryStatus status,
            Instant now
    ) {
        return entityManager.createNativeQuery("""
                UPDATE deliveries
                SET status = :status,
                    next_attempt_at = NULL,
                    completed_at = :now,
                    locked_at = NULL,
                    lock_token = NULL,
                    updated_at = :now
                WHERE id = :deliveryId
                  AND status = 'DELIVERING'
                  AND lock_token = :lockToken
                """)
                .setParameter("status", status.name())
                .setParameter("now", now)
                .setParameter("deliveryId", claim.deliveryId())
                .setParameter("lockToken", claim.lockToken())
                .executeUpdate();
    }
}

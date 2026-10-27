package me.talim.vayu.worker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryAttempt;

/**
 * Claims due deliveries in one short transaction. The row locks are released
 * when this method returns, long before any HTTP request is made.
 */
@Service
public class DeliveryClaimService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public List<ClaimedDelivery> claim(int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> due = entityManager.createNativeQuery("""
                SELECT id, attempt_count, max_attempts
                FROM deliveries
                WHERE status = 'PENDING'
                  AND next_attempt_at <= now()
                  AND attempt_count < max_attempts
                ORDER BY next_attempt_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """)
                .setParameter("batchSize", batchSize)
                .getResultList();

        if (due.isEmpty()) {
            return List.of();
        }

        UUID lockToken = UUID.randomUUID();
        Instant now = Instant.now();
        List<UUID> ids = due.stream()
                .map(row -> (UUID) row[0])
                .toList();

        entityManager.createNativeQuery("""
                UPDATE deliveries
                SET status = 'DELIVERING',
                    locked_at = :now,
                    lock_token = :lockToken,
                    attempt_count = attempt_count + 1,
                    updated_at = :now
                WHERE id IN (:ids)
                """)
                .setParameter("now", now)
                .setParameter("lockToken", lockToken)
                .setParameter("ids", ids)
                .executeUpdate();

        List<ClaimedDelivery> claimed = new ArrayList<>(due.size());
        for (Object[] row : due) {
            UUID deliveryId = (UUID) row[0];
            int attemptNumber = ((Number) row[1]).intValue() + 1;
            int maxAttempts = ((Number) row[2]).intValue();

            Delivery reference = entityManager.getReference(Delivery.class, deliveryId);
            entityManager.persist(new DeliveryAttempt(reference, attemptNumber, now));

            claimed.add(new ClaimedDelivery(deliveryId, attemptNumber, maxAttempts, lockToken));
        }

        return claimed;
    }
}

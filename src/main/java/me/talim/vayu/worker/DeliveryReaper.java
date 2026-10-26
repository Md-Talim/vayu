package me.talim.vayu.worker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import me.talim.vayu.models.Delivery;
import me.talim.vayu.repositories.DeliveryRepository;

/**
 * Releases deliveries whose worker stopped reporting. The lease token is
 * cleared here, so a late completion from the old worker is rejected.
 */
@Service
public class DeliveryReaper {

    private static final Logger log = LoggerFactory.getLogger(DeliveryReaper.class);
    private static final String LEASE_EXPIRED = "lease expired";

    @PersistenceContext
    private EntityManager entityManager;

    private final DeliveryRepository deliveryRepository;
    private final WorkerProperties properties;

    public DeliveryReaper(
            DeliveryRepository deliveryRepository,
            WorkerProperties properties
    ) {
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
    }

    @Transactional
    public int runOnce() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(properties.lease());

        @SuppressWarnings("unchecked")
        List<UUID> staleIds = entityManager.createNativeQuery("""
                SELECT id
                FROM deliveries
                WHERE status = 'DELIVERING'
                  AND locked_at < :cutoff
                ORDER BY locked_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """)
                .setParameter("cutoff", cutoff)
                .setParameter("batchSize", properties.reaper().batchSize())
                .getResultList();

        for (UUID deliveryId : staleIds) {
            Delivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
            if (delivery == null) {
                continue;
            }

            if (delivery.getAttemptCount() >= delivery.getMaxAttempts()) {
                delivery.markFailed(now);
            } else {
                delivery.scheduleRetry(now, now);
            }

            entityManager.createNativeQuery("""
                    UPDATE delivery_attempts
                    SET completed_at = :now, error = :error
                    WHERE delivery_id = :deliveryId
                      AND completed_at IS NULL
                    """)
                    .setParameter("now", now)
                    .setParameter("error", LEASE_EXPIRED)
                    .setParameter("deliveryId", deliveryId)
                    .executeUpdate();
        }

        if (!staleIds.isEmpty()) {
            log.info("Reaped {} stale deliveries", staleIds.size());
        }

        return staleIds.size();
    }
}

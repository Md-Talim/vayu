package me.talim.vayu.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryStatus;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    long countByEvent_Id(UUID eventId);

    @Query(
        value = """
            SELECT d FROM Delivery d
            JOIN FETCH d.event
            JOIN FETCH d.subscription s
            JOIN FETCH s.endpoint
            WHERE (:status IS NULL OR d.status = :status)
              AND (:endpointId IS NULL OR s.endpoint.id = :endpointId)
            """,
        countQuery = """
            SELECT count(d) FROM Delivery d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:endpointId IS NULL OR d.subscription.endpoint.id = :endpointId)
            """
    )
    Page<Delivery> findPage(
            @Param("status") DeliveryStatus status,
            @Param("endpointId") UUID endpointId,
            Pageable pageable
    );

    @Query("""
        SELECT d FROM Delivery d
        JOIN FETCH d.event
        JOIN FETCH d.subscription s
        JOIN FETCH s.endpoint
        LEFT JOIN FETCH d.attempts
        WHERE d.id = :id
        """)
    Optional<Delivery> findDetailedById(@Param("id") UUID id);

    @Query("""
        SELECT d FROM Delivery d
        JOIN FETCH d.event
        JOIN FETCH d.subscription s
        JOIN FETCH s.endpoint
        WHERE d.id = :id
          AND d.status = :status
          AND d.lockToken = :lockToken
        """)
    Optional<Delivery> findClaimedById(
            @Param("id") UUID id,
            @Param("status") DeliveryStatus status,
            @Param("lockToken") UUID lockToken
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Delivery d WHERE d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") UUID id);
}

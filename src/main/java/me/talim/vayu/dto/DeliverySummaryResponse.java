package me.talim.vayu.dto;

import java.time.Instant;
import java.util.UUID;

import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryStatus;

public record DeliverySummaryResponse(
    UUID id,
    UUID eventId,
    String eventType,
    UUID endpointId,
    String endpointUrl,
    DeliveryStatus status,
    int attemptCount,
    int maxAttempts,
    Instant nextAttemptAt,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
    public static DeliverySummaryResponse from(Delivery delivery) {
        return new DeliverySummaryResponse(
            delivery.getId(),
            delivery.getEvent().getId(),
            delivery.getEvent().getType(),
            delivery.getSubscription().getEndpoint().getId(),
            delivery.getSubscription().getEndpoint().getUrl(),
            delivery.getStatus(),
            delivery.getAttemptCount(),
            delivery.getMaxAttempts(),
            delivery.getNextAttemptAt(),
            delivery.getCreatedAt(),
            delivery.getUpdatedAt(),
            delivery.getCompletedAt()
        );
    }
}

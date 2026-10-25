package me.talim.vayu.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.DeliveryAttempt;
import me.talim.vayu.models.DeliveryStatus;

public record GetDeliveryResponse(
    UUID id,
    UUID eventId,
    String eventType,
    UUID endpointId,
    String endpointUrl,
    DeliveryStatus status,
    int attemptCount,
    int maxAttempts,
    Instant nextAttemptAt,
    Instant lockedAt,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    List<DeliveryAttemptResponse> attempts
) {
    public static GetDeliveryResponse from(Delivery delivery) {
        List<DeliveryAttemptResponse> attempts = delivery.getAttempts().stream()
                .sorted(Comparator.comparingInt(DeliveryAttempt::getAttemptNumber))
                .map(DeliveryAttemptResponse::from)
                .toList();

        return new GetDeliveryResponse(
            delivery.getId(),
            delivery.getEvent().getId(),
            delivery.getEvent().getType(),
            delivery.getSubscription().getEndpoint().getId(),
            delivery.getSubscription().getEndpoint().getUrl(),
            delivery.getStatus(),
            delivery.getAttemptCount(),
            delivery.getMaxAttempts(),
            delivery.getNextAttemptAt(),
            delivery.getLockedAt(),
            delivery.getCreatedAt(),
            delivery.getUpdatedAt(),
            delivery.getCompletedAt(),
            attempts
        );
    }
}

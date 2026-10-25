package me.talim.vayu.dto;

import java.time.Instant;
import java.util.UUID;

import me.talim.vayu.models.DeliveryAttempt;

public record DeliveryAttemptResponse(
    UUID id,
    int attemptNumber,
    Integer httpStatus,
    String error,
    Instant startedAt,
    Instant completedAt
) {
    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
            attempt.getId(),
            attempt.getAttemptNumber(),
            attempt.getHttpStatus(),
            attempt.getError(),
            attempt.getStartedAt(),
            attempt.getCompletedAt()
        );
    }
}

package me.talim.vayu.dto;

import java.time.Instant;
import java.util.UUID;

import me.talim.vayu.models.Event;

public record CreateEventResponse(
    UUID id,
    String type,
    String idempotencyKey,
    Instant createdAt,
    int deliveriesCreated
) {
    public static CreateEventResponse from(Event event, int deliveriesCreated) {
        return new CreateEventResponse(
            event.getId(),
            event.getType(),
            event.getIdempotencyKey(),
            event.getCreatedAt(),
            deliveriesCreated
        );
    }
}

package me.talim.vayu.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import me.talim.vayu.models.Endpoint;

public record GetEndpointResponse(
    UUID id,
    String url,
    boolean active,
    Set<String> eventTypes,
    Instant createdAt
) {
    public static GetEndpointResponse from(Endpoint endpoint, Set<String> eventTypes) {
        return new GetEndpointResponse(
            endpoint.getId(),
            endpoint.getUrl(),
            endpoint.isActive(),
            eventTypes,
            endpoint.getCreatedAt()
        );
    }
}

package me.talim.vayu.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import me.talim.vayu.models.Endpoint;

/**
 * This DTO is only for the response of
 * `POST /api/v1/endpoints` route because it includes the secret
 */
public record CreateEndpointResponse(
    UUID id,
    String url,
    boolean active,
    Set<String> eventTypes,
    Instant createdAt,
    String secret
) {
    public static CreateEndpointResponse from(Endpoint endpoint, Set<String> eventTypes) {
        return new CreateEndpointResponse(
            endpoint.getId(),
            endpoint.getUrl(),
            endpoint.isActive(),
            eventTypes,
            endpoint.getCreatedAt(),
            endpoint.getSecret()
        );
    }
}

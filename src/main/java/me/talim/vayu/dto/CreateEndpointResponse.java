package me.talim.vayu.dto;

import java.time.Instant;
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
    Instant createdAt,
    String secret
) {
    public static CreateEndpointResponse from(Endpoint endpoint) {
        return new CreateEndpointResponse(
            endpoint.getId(),
            endpoint.getUrl(),
            endpoint.isActive(),
            endpoint.getCreatedAt(),
            endpoint.getSecret()
        );
    }
}

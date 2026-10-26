package me.talim.vayu.worker;

import java.util.UUID;

public record DeliveryContext(
        UUID deliveryId,
        UUID eventId,
        String eventType,
        String payload,
        String endpointUrl,
        String secret,
        boolean endpointActive
) {}

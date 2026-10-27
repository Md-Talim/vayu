package me.talim.vayu.worker;

import java.util.UUID;

public record ClaimedDelivery(
        UUID deliveryId,
        int attemptNumber,
        int maxAttempts,
        UUID lockToken
) {}

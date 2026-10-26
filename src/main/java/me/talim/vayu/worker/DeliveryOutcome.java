package me.talim.vayu.worker;

import java.time.Duration;

public record DeliveryOutcome(
        Type type,
        Integer httpStatus,
        String responseBody,
        String error,
        Duration retryAfter
) {

    public enum Type {
        DELIVERED,
        RETRY,
        FAILED
    }

    public static DeliveryOutcome delivered(Integer httpStatus, String responseBody) {
        return new DeliveryOutcome(Type.DELIVERED, httpStatus, responseBody, null, null);
    }

    public static DeliveryOutcome retry(
            Integer httpStatus,
            String responseBody,
            String error,
            Duration retryAfter
    ) {
        return new DeliveryOutcome(Type.RETRY, httpStatus, responseBody, error, retryAfter);
    }

    public static DeliveryOutcome failed(
            Integer httpStatus,
            String responseBody,
            String error
    ) {
        return new DeliveryOutcome(Type.FAILED, httpStatus, responseBody, error, null);
    }
}

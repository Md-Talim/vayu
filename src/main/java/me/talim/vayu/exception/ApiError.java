package me.talim.vayu.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

public record ApiError(
    Instant timestamp,
    int status,
    String code,
    String message,
    List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(HttpStatus status, String code, String message) {
        return new ApiError(Instant.now(), status.value(), code, message, List.of());
    }

    public static ApiError withFieldErrors(
            HttpStatus status,
            String code,
            String message,
            List<FieldError> fieldErrors
    ) {
        return new ApiError(Instant.now(), status.value(), code, message, fieldErrors);
    }
}

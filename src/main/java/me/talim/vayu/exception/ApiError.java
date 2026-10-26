package me.talim.vayu.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiError", description = "Error response returned by the API")
public record ApiError(
    @Schema(description = "Time the error occurred")
    Instant timestamp,

    @Schema(description = "HTTP status code", example = "404")
    int status,

    @Schema(description = "Stable error code", example = "NOT_FOUND")
    String code,

    @Schema(description = "Human-readable error message")
    String message,

    @Schema(description = "Field-level validation errors")
    List<FieldError> fieldErrors
) {
    @Schema(name = "ApiFieldError", description = "Field-level validation error")
    public record FieldError(
        @Schema(description = "Field or parameter name", example = "type")
        String field,

        @Schema(description = "Validation message", example = "type is required")
        String message
    ) {}

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

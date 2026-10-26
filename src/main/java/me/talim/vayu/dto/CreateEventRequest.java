package me.talim.vayu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateEventRequest", description = "Event creation request")
public record CreateEventRequest(
    @Schema(description = "Event type", example = "payment.succeeded")
    @NotBlank(message = "type is required") @Size(max = 128) String type,

    @Schema(
        description = "Event payload as a JSON string",
        example = "{\"amount\":100,\"currency\":\"USD\"}"
    )
    @NotEmpty(message = "payload is required") String payload
) {}

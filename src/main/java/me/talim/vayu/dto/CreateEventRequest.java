package me.talim.vayu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
    @NotBlank(message = "type is required") @Size(max = 128) String type,

    @NotEmpty(message = "payload is required") String payload
) {}

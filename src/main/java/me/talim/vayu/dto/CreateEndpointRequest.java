package me.talim.vayu.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import me.talim.vayu.validation.ValidWebhookUrl;

public record CreateEndpointRequest(
    @NotBlank(message = "url is required") @Size(max = 2048) @ValidWebhookUrl String url,

    @NotEmpty(message = "eventTypes is required")
    @Size(max = 50)
    Set<@NotBlank @Size(max = 128) String> eventTypes
) {}

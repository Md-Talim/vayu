package me.talim.vayu.dto;

import jakarta.validation.constraints.NotBlank;
import me.talim.vayu.validation.ValidWebhookUrl;

public record CreateEndpointRequest(
    @NotBlank(message = "url is required") @ValidWebhookUrl String url
) {}

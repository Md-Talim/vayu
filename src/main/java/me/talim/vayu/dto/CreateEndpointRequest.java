package me.talim.vayu.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import me.talim.vayu.validation.ValidWebhookUrl;

@Schema(name = "CreateEndpointRequest", description = "Endpoint creation request")
public record CreateEndpointRequest(
    @Schema(description = "HTTP or HTTPS webhook URL", example = "https://example.com/webhooks")
    @NotBlank(message = "url is required") @Size(max = 2048) @ValidWebhookUrl String url,

    @Schema(description = "Event types this endpoint subscribes to", example = "[\"payment.succeeded\"]")
    @NotEmpty(message = "eventTypes is required")
    @Size(max = 50)
    Set<@NotBlank @Size(max = 128) String> eventTypes
) {}

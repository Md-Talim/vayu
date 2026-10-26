package me.talim.vayu.worker;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WebhookSender {

    static final int MAX_RESPONSE_BODY = 1024;
    static final int MAX_ERROR = 512;

    private final RestClient restClient;
    private final WebhookSigner signer;

    public WebhookSender(RestClient deliveryRestClient, WebhookSigner signer) {
        this.restClient = deliveryRestClient;
        this.signer = signer;
    }

    public DeliveryOutcome send(DeliveryContext context, int attemptNumber) {
        if (!context.endpointActive()) {
            return DeliveryOutcome.failed(null, null, "endpoint inactive");
        }

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = signer.sign(context.secret(), timestamp, context.payload());

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(context.endpointUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Vayu-Event-Id", context.eventId().toString())
                    .header("X-Vayu-Event-Type", context.eventType())
                    .header("X-Vayu-Delivery-Id", context.deliveryId().toString())
                    .header("X-Vayu-Attempt", Integer.toString(attemptNumber))
                    .header("X-Vayu-Timestamp", timestamp)
                    .header("X-Vayu-Signature", "v1=" + signature)
                    .body(context.payload())
                    .retrieve()
                    .toEntity(String.class);

            return classify(
                    response.getStatusCode().value(),
                    response.getBody(),
                    null,
                    response.getHeaders()
            );
        } catch (RestClientResponseException exception) {
            return classify(
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(),
                    "receiver returned " + exception.getStatusCode().value(),
                    exception.getResponseHeaders()
            );
        } catch (ResourceAccessException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return DeliveryOutcome.retry(null, null, truncate(message, MAX_ERROR), null);
        }
    }

    private DeliveryOutcome classify(
            int status,
            String body,
            String error,
            HttpHeaders headers
    ) {
        String responseBody = truncate(body, MAX_RESPONSE_BODY);

        if (status >= 200 && status < 300) {
            return DeliveryOutcome.delivered(status, responseBody);
        }

        Duration retryAfter = retryAfter(headers);
        String message = error == null ? "receiver returned " + status : error;

        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return DeliveryOutcome.retry(status, responseBody, message, retryAfter);
        }

        return DeliveryOutcome.failed(status, responseBody, message);
    }

    private Duration retryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }

        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException ignored) {
            try {
                Instant at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant();
                Duration delay = Duration.between(Instant.now(), at);
                return delay.isNegative() || delay.isZero() ? null : delay;
            } catch (DateTimeParseException ignoredToo) {
                return null;
            }
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

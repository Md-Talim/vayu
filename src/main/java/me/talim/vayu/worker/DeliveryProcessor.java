package me.talim.vayu.worker;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import me.talim.vayu.observability.VayuMetrics;

@Component
public class DeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessor.class);
    private static final int MAX_ERROR = 512;

    private final DeliveryLookupService lookupService;
    private final WebhookSender sender;
    private final DeliveryCompletionService completionService;
    private final VayuMetrics metrics;

    public DeliveryProcessor(
            DeliveryLookupService lookupService,
            WebhookSender sender,
            DeliveryCompletionService completionService,
            VayuMetrics metrics
    ) {
        this.lookupService = lookupService;
        this.sender = sender;
        this.completionService = completionService;
        this.metrics = metrics;
    }

    public void process(ClaimedDelivery claim) {
        try (
                MDC.MDCCloseable deliveryId = MDC.putCloseable(
                        "deliveryId",
                        claim.deliveryId().toString()
                );
                MDC.MDCCloseable attempt = MDC.putCloseable(
                        "attempt",
                        Integer.toString(claim.attemptNumber())
                )
        ) {
            try {
                DeliveryContext context = lookupService.load(claim).orElse(null);
                if (context == null) {
                    log.debug("Delivery is no longer claimed, skipping");
                    return;
                }

                try (
                        MDC.MDCCloseable eventId = MDC.putCloseable(
                                "eventId",
                                context.eventId().toString()
                        );
                        MDC.MDCCloseable eventType = MDC.putCloseable(
                                "eventType",
                                context.eventType()
                        )
                ) {
                    process(context, claim);
                }
            } catch (RuntimeException exception) {
                log.error("Delivery attempt could not be completed", exception);
            }
        }
    }

    private void process(DeliveryContext context, ClaimedDelivery claim) {
        DeliveryOutcome outcome;

        try {
            outcome = sender.send(context, claim.attemptNumber());
        } catch (RuntimeException exception) {
            log.warn("Delivery attempt failed before a response", exception);
            outcome = DeliveryOutcome.retry(
                    null,
                    null,
                    errorMessage(exception),
                    null
            );
        }

        String outcomeValue = outcome.type().name().toLowerCase(Locale.ROOT);
        metrics.recordDeliveryAttempt(outcomeValue);
        logOutcome(outcomeValue, outcome);

        if (!completionService.complete(claim, outcome)) {
            log.info("Delivery completion was rejected because the lease changed");
        }
    }

    private void logOutcome(String value, DeliveryOutcome outcome) {
        try (MDC.MDCCloseable outcomeMdc = MDC.putCloseable("outcome", value)) {
            if (outcome.type() == DeliveryOutcome.Type.DELIVERED) {
                log.info("Delivery attempt completed");
            } else {
                log.warn("Delivery attempt completed");
            }
        }
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();

        return message.length() <= MAX_ERROR ? message : message.substring(0, MAX_ERROR);
    }
}

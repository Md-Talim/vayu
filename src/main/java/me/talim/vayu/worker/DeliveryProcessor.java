package me.talim.vayu.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessor.class);
    private static final int MAX_ERROR = 512;

    private final DeliveryLookupService lookupService;
    private final WebhookSender sender;
    private final DeliveryCompletionService completionService;

    public DeliveryProcessor(
            DeliveryLookupService lookupService,
            WebhookSender sender,
            DeliveryCompletionService completionService
    ) {
        this.lookupService = lookupService;
        this.sender = sender;
        this.completionService = completionService;
    }

    public void process(ClaimedDelivery claim) {
        try {
            DeliveryContext context = lookupService.load(claim).orElse(null);
            if (context == null) {
                log.debug(
                        "Delivery {} is no longer claimed, skipping",
                        claim.deliveryId()
                );
                return;
            }

            DeliveryOutcome outcome;
            try {
                outcome = sender.send(context, claim.attemptNumber());
            } catch (RuntimeException exception) {
                log.warn(
                        "Delivery {} attempt {} failed before a response",
                        claim.deliveryId(),
                        claim.attemptNumber(),
                        exception
                );
                outcome = DeliveryOutcome.retry(
                        null,
                        null,
                        errorMessage(exception),
                        null
                );
            }

            completionService.complete(claim, outcome);
        } catch (RuntimeException exception) {
            log.error(
                    "Delivery {} attempt {} could not be completed",
                    claim.deliveryId(),
                    claim.attemptNumber(),
                    exception
            );
        }
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();

        return message.length() <= MAX_ERROR ? message : message.substring(0, MAX_ERROR);
    }
}

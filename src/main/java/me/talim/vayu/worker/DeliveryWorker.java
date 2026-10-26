package me.talim.vayu.worker;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryClaimService claimService;
    private final DeliveryProcessor processor;
    private final ThreadPoolTaskExecutor executor;
    private final WorkerProperties properties;

    public DeliveryWorker(
            DeliveryClaimService claimService,
            DeliveryProcessor processor,
            @Qualifier("deliveryExecutor") ThreadPoolTaskExecutor executor,
            WorkerProperties properties
    ) {
        this.claimService = claimService;
        this.processor = processor;
        this.executor = executor;
        this.properties = properties;
    }

    public int runOnce() {
        List<ClaimedDelivery> claimed;

        try {
            claimed = claimService.claim(properties.batchSize());
        } catch (RuntimeException exception) {
            log.error("Could not claim due deliveries", exception);
            return 0;
        }

        for (ClaimedDelivery delivery : claimed) {
            try {
                executor.execute(() -> processor.process(delivery));
            } catch (TaskRejectedException exception) {
                log.error(
                        "Delivery {} was claimed but not submitted",
                        delivery.deliveryId(),
                        exception
                );
            }
        }

        if (!claimed.isEmpty()) {
            log.debug("Claimed {} deliveries", claimed.size());
        }

        return claimed.size();
    }
}

package me.talim.vayu.worker;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Timer;
import me.talim.vayu.observability.VayuMetrics;

@Service
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryClaimService claimService;
    private final DeliveryProcessor processor;
    private final ThreadPoolTaskExecutor executor;
    private final WorkerProperties properties;
    private final VayuMetrics metrics;

    public DeliveryWorker(
            DeliveryClaimService claimService,
            DeliveryProcessor processor,
            @Qualifier("deliveryExecutor") ThreadPoolTaskExecutor executor,
            WorkerProperties properties,
            VayuMetrics metrics
    ) {
        this.claimService = claimService;
        this.processor = processor;
        this.executor = executor;
        this.properties = properties;
        this.metrics = metrics;
    }

    public int runOnce() {
        Timer.Sample sample = metrics.startPollTimer();
        boolean success = false;

        try {
            List<ClaimedDelivery> claimed;

            try {
                claimed = claimService.claim(properties.batchSize());
            } catch (RuntimeException exception) {
                log.error("Could not claim due deliveries", exception);
                return 0;
            }

            metrics.recordClaimed(claimed.size());
            boolean rejected = false;

            for (ClaimedDelivery delivery : claimed) {
                try {
                    executor.execute(() -> processor.process(delivery));
                } catch (TaskRejectedException exception) {
                    rejected = true;
                    try (
                            MDC.MDCCloseable deliveryId = MDC.putCloseable(
                                    "deliveryId",
                                    delivery.deliveryId().toString()
                            );
                            MDC.MDCCloseable attempt = MDC.putCloseable(
                                    "attempt",
                                    Integer.toString(delivery.attemptNumber())
                            )
                    ) {
                        log.error("Delivery was claimed but not submitted", exception);
                    }
                }
            }

            if (!claimed.isEmpty()) {
                log.debug("Claimed {} deliveries", claimed.size());
            }

            success = !rejected;
            return claimed.size();
        } finally {
            metrics.recordPoll(sample, success);
        }
    }
}

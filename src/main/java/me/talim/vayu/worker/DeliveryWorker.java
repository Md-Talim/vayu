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
        boolean success = true;
        int totalClaimed = 0;

        try {
            for (int batch = 0; batch < properties.maxBatchesPerPoll(); batch++) {
                // Stop before claiming rows that cannot fit in the executor queue.
                if (properties.queueCapacity() > 0
                        && executor.getThreadPoolExecutor().getQueue().remainingCapacity()
                        < properties.batchSize()) {
                    break;
                }

                List<ClaimedDelivery> claimed;

                try {
                    claimed = claimService.claim(properties.batchSize());
                } catch (RuntimeException exception) {
                    log.error("Could not claim due deliveries", exception);
                    success = false;
                    return totalClaimed;
                }

                if (claimed.isEmpty()) {
                    break;
                }

                totalClaimed += claimed.size();
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

                if (rejected) {
                    success = false;
                    break;
                }

                if (claimed.size() < properties.batchSize()) {
                    break;
                }
            }

            if (totalClaimed > 0) {
                log.debug("Claimed {} deliveries", totalClaimed);
            }

            return totalClaimed;
        } finally {
            metrics.recordPoll(sample, success);
        }
    }
}

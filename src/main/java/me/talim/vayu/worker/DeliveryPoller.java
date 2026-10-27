package me.talim.vayu.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "vayu.worker.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DeliveryPoller {

    private final DeliveryWorker worker;

    public DeliveryPoller(DeliveryWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${vayu.worker.poll-interval:100ms}")
    public void poll() {
        worker.runOnce();
    }
}

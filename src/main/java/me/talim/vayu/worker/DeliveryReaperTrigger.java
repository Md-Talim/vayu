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
public class DeliveryReaperTrigger {

    private final DeliveryReaper reaper;
    private final WorkerProperties properties;

    public DeliveryReaperTrigger(DeliveryReaper reaper, WorkerProperties properties) {
        this.reaper = reaper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${vayu.worker.reaper.interval:30s}")
    public void reap() {
        if (properties.reaper().enabled()) {
            reaper.runOnce();
        }
    }
}

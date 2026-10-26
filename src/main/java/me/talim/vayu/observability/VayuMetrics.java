package me.talim.vayu.observability;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class VayuMetrics {

    private final MeterRegistry registry;

    public VayuMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startHttpTimer() {
        return Timer.start(registry);
    }

    public void recordHttp(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder("vayu.delivery.http.duration")
                .description("Time spent sending a webhook request")
                .tag("outcome", outcome)
                .register(registry));
    }

    public void recordDeliveryAttempt(String outcome) {
        Counter.builder("vayu.delivery.attempts")
                .description("Webhook delivery attempts by outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public Timer.Sample startPollTimer() {
        return Timer.start(registry);
    }

    public void recordPoll(Timer.Sample sample, boolean success) {
        sample.stop(Timer.builder("vayu.worker.poll.duration")
                .description("Time spent in one worker poll")
                .tag("result", success ? "success" : "error")
                .register(registry));
    }

    public void recordClaimed(int count) {
        if (count <= 0) {
            return;
        }

        Counter.builder("vayu.worker.claimed.deliveries")
                .description("Deliveries claimed by the worker")
                .register(registry)
                .increment(count);
    }
}

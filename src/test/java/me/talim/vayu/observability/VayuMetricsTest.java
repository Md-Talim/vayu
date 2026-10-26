package me.talim.vayu.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class VayuMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final VayuMetrics metrics = new VayuMetrics(registry);

    @Test
    void recordsDeliveryAttemptsByOutcome() {
        metrics.recordDeliveryAttempt("delivered");
        metrics.recordDeliveryAttempt("retry");
        metrics.recordDeliveryAttempt("retry");

        assertThat(registry
                .get("vayu.delivery.attempts")
                .tag("outcome", "delivered")
                .counter()
                .count()
        ).isEqualTo(1.0);
        assertThat(registry
                .get("vayu.delivery.attempts")
                .tag("outcome", "retry")
                .counter()
                .count()
        ).isEqualTo(2.0);
    }

    @Test
    void recordsHttpTimersByOutcome() {
        Timer.Sample sample = metrics.startHttpTimer();

        metrics.recordHttp(sample, "failed");

        assertThat(registry
                .get("vayu.delivery.http.duration")
                .tag("outcome", "failed")
                .timer()
                .count()
        ).isEqualTo(1);
    }

    @Test
    void recordsPollTimersByResult() {
        Timer.Sample success = metrics.startPollTimer();
        metrics.recordPoll(success, true);

        Timer.Sample failure = metrics.startPollTimer();
        metrics.recordPoll(failure, false);

        assertThat(registry
                .get("vayu.worker.poll.duration")
                .tag("result", "success")
                .timer()
                .count()
        ).isEqualTo(1);
        assertThat(registry
                .get("vayu.worker.poll.duration")
                .tag("result", "error")
                .timer()
                .count()
        ).isEqualTo(1);
    }

    @Test
    void recordsClaimedDeliveries() {
        metrics.recordClaimed(3);

        assertThat(registry
                .get("vayu.worker.claimed.deliveries")
                .counter()
                .count()
        ).isEqualTo(3.0);
    }

    @Test
    void doesNotRecordAnEmptyClaim() {
        metrics.recordClaimed(0);

        assertThat(registry.find("vayu.worker.claimed.deliveries").counter()).isNull();
    }
}

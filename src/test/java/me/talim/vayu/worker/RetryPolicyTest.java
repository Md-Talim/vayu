package me.talim.vayu.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final WorkerProperties.Retry RETRY = new WorkerProperties.Retry(
            Duration.ofSeconds(1),
            Duration.ofMinutes(1),
            2.0
    );

    @Test
    void growsExponentiallyAndCapsAtTheMaximum() {
        RetryPolicy policy = new RetryPolicy(RETRY, () -> 0.5);

        assertThat(policy.nextDelay(1, null)).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.nextDelay(2, null)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.nextDelay(3, null)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.nextDelay(20, null)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void appliesFullJitter() {
        RetryPolicy noJitter = new RetryPolicy(RETRY, () -> 0.0);
        RetryPolicy halfJitter = new RetryPolicy(RETRY, () -> 0.5);

        assertThat(noJitter.nextDelay(1, null)).isZero();
        assertThat(halfJitter.nextDelay(1, null)).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void respectsRetryAfterWithoutExceedingTheCap() {
        RetryPolicy policy = new RetryPolicy(RETRY, () -> 0.5);

        assertThat(policy.nextDelay(1, Duration.ofSeconds(10)))
                .isEqualTo(Duration.ofSeconds(10));
        assertThat(policy.nextDelay(1, Duration.ofMinutes(5)))
                .isEqualTo(Duration.ofMinutes(1));
    }
}

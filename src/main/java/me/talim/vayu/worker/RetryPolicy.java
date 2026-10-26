package me.talim.vayu.worker;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private final WorkerProperties.Retry properties;
    private final DoubleSupplier jitter;

    @Autowired
    public RetryPolicy(WorkerProperties properties) {
        this(properties.retry(), () -> ThreadLocalRandom.current().nextDouble());
    }

    RetryPolicy(WorkerProperties.Retry properties, DoubleSupplier jitter) {
        this.properties = properties;
        this.jitter = jitter;
    }

    public Duration nextDelay(int attemptNumber, Duration retryAfter) {
        double exponential = properties.baseDelay().toMillis()
                * Math.pow(properties.multiplier(), Math.max(0, attemptNumber - 1));
        long capped = (long) Math.min(properties.maxDelay().toMillis(), exponential);
        long fullJitter = (long) (capped * jitter.getAsDouble());

        if (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()) {
            fullJitter = Math.max(fullJitter, retryAfter.toMillis());
        }

        long bounded = Math.min(properties.maxDelay().toMillis(), fullJitter);
        return Duration.ofMillis(Math.max(0, bounded));
    }
}

package me.talim.vayu.worker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "vayu.worker")
public record WorkerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100ms") @NotNull Duration pollInterval,
        @DefaultValue("20") @Min(1) int batchSize,
        @DefaultValue("16") @Min(1) int concurrency,
        @DefaultValue("200") @Min(0) int queueCapacity,
        @DefaultValue("10") @Min(1) int maxBatchesPerPoll,
        @DefaultValue("2s") @NotNull Duration connectTimeout,
        @DefaultValue("5s") @NotNull Duration readTimeout,
        @DefaultValue("60s") @NotNull Duration lease,
        @DefaultValue @Valid Retry retry,
        @DefaultValue @Valid Reaper reaper
) {

    public record Retry(
            @DefaultValue("2s") @NotNull Duration baseDelay,
            @DefaultValue("5m") @NotNull Duration maxDelay,
            @DefaultValue("2.0") @DecimalMin("1.0") double multiplier
    ) {}

    public record Reaper(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("30s") @NotNull Duration interval,
            @DefaultValue("50") @Min(1) int batchSize
    ) {}
}

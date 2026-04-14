package io.github.closeup1202.lofi.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for lofi, bound from the {@code lofi.*} namespace.
 *
 * <p>Validated via JSR-303 during {@link ConfigurationProperties} binding.
 * All constraint violations are reported at once on startup rather than
 * stopping at the first failure.
 *
 * @param commitHash          git commit hash identifying the current deploy.
 *                            Populated via the {@code GIT_COMMIT_HASH} environment variable.
 *                            Defaults to {@code "unknown"} if not set.
 * @param storeType           storage backend for collected metrics.
 *                            Accepted values: {@code "sqlite"} (default, persistent) or
 *                            {@code "in-memory"} (ephemeral, for test/dev use).
 * @param regressionThreshold relative latency increase required to flag a method as regressed.
 *                            For example, {@code 0.2} means a 20% increase triggers a regression.
 *                            Must be between 0 and 1. Defaults to {@code 0.2}.
 * @param retentionCommits    number of recent deploys to retain in storage.
 *                            Older deploys are evicted automatically. Defaults to {@code 50}.
 * @param buffer              buffer settings controlling how metrics are batched before writing to storage
 */
@Validated
@ConfigurationProperties(prefix = "lofi")
public record LofiProperties(
        @DefaultValue("unknown") String commitHash,

        @Pattern(regexp = "sqlite|in-memory", message = "must be 'sqlite' or 'in-memory'")
        @DefaultValue("sqlite") String storeType,

        @DecimalMin(value = "0.0", message = "must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "must be between 0 and 1")
        @DefaultValue("0.2") double regressionThreshold,

        @Min(value = 1, message = "must be at least 1")
        @DefaultValue("50") int retentionCommits,

        @Valid
        @DefaultValue Buffer buffer
) {
    /**
     * Buffer settings that control how metrics are accumulated before being written to storage.
     *
     * @param flushThreshold number of buffered metrics that triggers an immediate flush.
     *                       Defaults to {@code 100}.
     * @param flushDelayMs   interval in milliseconds between periodic flushes regardless of queue size.
     *                       Defaults to {@code 5000}.
     * @param queueCapacity  maximum number of metrics the buffer can hold before an overflow flush is attempted.
     *                       Defaults to {@code 1000}.
     */
    public record Buffer(
            @Min(value = 1, message = "must be at least 1")
            @DefaultValue("100") int flushThreshold,

            @Min(value = 1, message = "must be at least 1")
            @DefaultValue("5000") long flushDelayMs,

            @Min(value = 1, message = "must be at least 1")
            @DefaultValue("1000") int queueCapacity
    ) {}
}

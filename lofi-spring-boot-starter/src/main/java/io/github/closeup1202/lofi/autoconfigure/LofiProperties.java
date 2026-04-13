package io.github.closeup1202.lofi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for lofi, bound from the {@code lofi.*} namespace.
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
@ConfigurationProperties(prefix = "lofi")
public record LofiProperties(
        @DefaultValue("unknown") String commitHash,
        @DefaultValue("sqlite") String storeType,
        @DefaultValue("0.2") double regressionThreshold,
        @DefaultValue("50") int retentionCommits,
        @DefaultValue Buffer buffer
) {
    public LofiProperties {
        if (!storeType.equals("sqlite") && !storeType.equals("in-memory")) {
            throw new IllegalArgumentException(
                    "lofi.store-type must be 'sqlite' or 'in-memory', got: " + storeType
            );
        }

        if (regressionThreshold < 0 || regressionThreshold > 1) {
            throw new IllegalArgumentException(
                    "lofi.regression-threshold must be between 0 and 1, got: " + regressionThreshold
            );
        }

        if (retentionCommits < 1) {
            throw new IllegalArgumentException(
                    "lofi.retention-commits must be at least 1, got: " + retentionCommits
            );
        }
    }

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
            @DefaultValue("100") int flushThreshold,
            @DefaultValue("5000") long flushDelayMs,
            @DefaultValue("1000") int queueCapacity
    ) {
        public Buffer {
            if (flushThreshold < 1) {
                throw new IllegalArgumentException(
                        "lofi.buffer.flush-threshold must be at least 1, got: " + flushThreshold
                );
            }
            if (flushDelayMs < 1) {
                throw new IllegalArgumentException(
                        "lofi.buffer.flush-delay-ms must be at least 1, got: " + flushDelayMs
                );
            }
            if (queueCapacity < 1) {
                throw new IllegalArgumentException(
                        "lofi.buffer.queue-capacity must be at least 1, got: " + queueCapacity
                );
            }
        }
    }
}

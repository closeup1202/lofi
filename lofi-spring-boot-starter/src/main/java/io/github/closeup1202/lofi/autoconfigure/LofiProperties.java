package io.github.closeup1202.lofi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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

    public record Buffer(
            @DefaultValue("100") int flushThreshold,
            @DefaultValue("5000") long flushDelayMs,
            @DefaultValue("1000") int queueCapacity
    ) {
    }
}

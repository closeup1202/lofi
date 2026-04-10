package io.github.closeup1202.lofi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lofi")
public record LofiProperties(
        @DefaultValue("unknown") String commitHash,
        @DefaultValue("sqlite") String storeType,
        @DefaultValue("0.2") double regressionThreshold,
        @DefaultValue Buffer buffer
) {
    public LofiProperties {
        if (!storeType.equals("sqlite") && !storeType.equals("in-memory")) {
            throw new IllegalArgumentException(
                    "lofi.store-type must be 'sqlite' or 'in-memory', got: " + storeType
            );
        }
    }

    public record Buffer(
            @DefaultValue("100") int flushThreshold,
            @DefaultValue("5000") long flushDelayMs,
            @DefaultValue("1000") int queueCapacity
    ) {}
}

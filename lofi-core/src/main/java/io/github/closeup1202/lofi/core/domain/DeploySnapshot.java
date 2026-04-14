package io.github.closeup1202.lofi.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A snapshot of all method metrics collected for a single deploy.
 * Each deploy is identified by its commit hash, and the snapshot aggregates
 * every {@link MethodMetric} recorded while that commit was running.
 *
 * @param commitHash the git commit hash identifying this deploy
 * @param deployedAt timestamp of the earliest recorded metric, used as a proxy for deploy time
 * @param metrics    all raw method measurements captured for this deploy
 */
public record DeploySnapshot(
        String commitHash,
        Instant deployedAt,
        List<MethodMetric> metrics
) {
    /**
     * Computes the average elapsed time per method signature across all recorded metrics.
     *
     * @return a map from method signature to average latency in nanoseconds
     */
    public Map<String, Double> averageByMethod() {
        return metrics.stream()
                .collect(Collectors.groupingBy(
                        MethodMetric::signature,
                        Collectors.averagingLong(MethodMetric::elapsedNs)
                ));
    }
}

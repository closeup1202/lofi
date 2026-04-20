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
     * Computes avg, P95, P99, and call count per method signature.
     *
     * @return a map from method signature to {@link MethodStats}
     */
    public Map<String, MethodStats> statsByMethod() {
        return metrics.stream()
                .collect(Collectors.groupingBy(MethodMetric::signature))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            List<Long> sorted = e.getValue().stream()
                                    .map(MethodMetric::elapsedNs)
                                    .sorted()
                                    .toList();
                            int n = sorted.size();
                            double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
                            // Same formula as SQL: (n * 95 + 99) / 100 gives 1-indexed row; subtract 1 for 0-indexed
                            double p95 = sorted.get(Math.max(0, (n * 95 + 99) / 100 - 1));
                            double p99 = sorted.get(Math.max(0, (n * 99 + 99) / 100 - 1));
                            return new MethodStats(avg, p95, p99, n);
                        }
                ));
    }
}

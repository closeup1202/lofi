package io.github.closeup1202.lofi.core.view;

import io.github.closeup1202.lofi.core.domain.MethodStats;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

public record DeploySnapshotView(
        String commitHash,
        Instant deployedAt,
        Map<String, MethodStatsView> methods
) {
    public static DeploySnapshotView from(String commitHash, Instant deployedAt, Map<String, MethodStats> stats) {
        Map<String, MethodStatsView> methods = stats.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> MethodStatsView.from(e.getValue())));
        return new DeploySnapshotView(commitHash, deployedAt, methods);
    }
}

package io.github.closeup1202.lofi.core.view;

import io.github.closeup1202.lofi.core.domain.MethodStats;

public record MethodStatsView(double avgMs, double p95Ms, double p99Ms, int count) {

    public static MethodStatsView from(MethodStats stats) {
        return new MethodStatsView(
                stats.avgNs() / 1_000_000.0,
                stats.p95Ns() / 1_000_000.0,
                stats.p99Ns() / 1_000_000.0,
                stats.count()
        );
    }
}

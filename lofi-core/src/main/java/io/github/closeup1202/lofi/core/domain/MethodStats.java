package io.github.closeup1202.lofi.core.domain;

/**
 * Aggregated latency statistics for a single method in one deploy.
 *
 * @param avgNs average elapsed time in nanoseconds
 * @param p95Ns 95th-percentile elapsed time in nanoseconds
 * @param p99Ns 99th-percentile elapsed time in nanoseconds
 * @param count number of invocations recorded
 */
public record MethodStats(double avgNs, double p95Ns, double p99Ns, int count) {
}

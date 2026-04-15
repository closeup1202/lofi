package io.github.closeup1202.lofi.core.domain;

import java.time.Instant;

/**
 * Lightweight summary of a single deploy — commit hash, first-seen time, and total metric count.
 * Used to populate the commit list endpoint without loading all raw metrics.
 */
public record CommitSummary(String commitHash, Instant deployedAt, long metricCount) {
}

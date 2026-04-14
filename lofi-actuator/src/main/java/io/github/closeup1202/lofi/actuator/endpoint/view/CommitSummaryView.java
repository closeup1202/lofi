package io.github.closeup1202.lofi.actuator.endpoint.view;

import io.github.closeup1202.lofi.core.domain.CommitSummary;

import java.time.Instant;

/**
 * HTTP response view for a single commit summary.
 * Returned by {@code GET /actuator/lofi} as part of the commit list.
 */
public record CommitSummaryView(String commitHash, Instant deployedAt, long metricCount) {

    public static CommitSummaryView from(CommitSummary summary) {
        return new CommitSummaryView(summary.commitHash(), summary.deployedAt(), summary.metricCount());
    }
}

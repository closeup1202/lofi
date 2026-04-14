package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;

import java.util.List;

/**
 * Read-only port for querying recorded deploy metrics.
 * Implemented by both the collector-side store and the lofi-backend store.
 */
public interface ReadableMetricStore {

    /**
     * Retrieves all metrics recorded for the given commit hash as a snapshot.
     *
     * @param commitHash the commit hash identifying the deploy
     * @return a snapshot containing all metrics and the inferred deploy time
     */
    DeploySnapshot snapshot(String commitHash);

    /**
     * Returns a summary of all recorded deploys, ordered by deploy time descending.
     *
     * @return list of commit summaries
     */
    List<CommitSummary> listCommits();
}

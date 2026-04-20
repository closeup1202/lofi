package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodStats;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read-only port for querying recorded deploy metrics.
 * Implemented by both the collector-side store and the lofi-backend store.
 */
public interface ReadableMetricStore {

    /**
     * Retrieves all metrics recorded for the given commit hash as a snapshot.
     * Used by the snapshot endpoint to show individual measurements.
     *
     * @param commitHash the commit hash identifying the deploy
     * @return a snapshot containing all metrics and the inferred deploy time
     */
    DeploySnapshot snapshot(String commitHash);

    /**
     * Returns aggregated latency statistics per method signature for the given commit.
     * Used by the diff engine — implementations should compute avg/p95/p99 at the
     * storage level to avoid loading all raw rows into memory.
     *
     * <p>The default implementation delegates to {@link #snapshot} for backward
     * compatibility. SQLite-backed stores override this with a window-function query.
     *
     * @param commitHash the commit hash identifying the deploy
     * @return map from method signature to aggregated stats
     */
    default Map<String, MethodStats> statsByMethod(String commitHash) {
        return snapshot(commitHash).statsByMethod();
    }

    /**
     * Returns the earliest recorded timestamp for the given commit, used as a proxy for deploy time.
     * The default implementation delegates to {@link #snapshot}; SQLite-backed stores override this
     * with a lightweight {@code MIN(recorded_at)} query.
     *
     * @param commitHash the commit hash identifying the deploy
     * @return deploy time, or {@link Instant#EPOCH} if no metrics exist
     */
    default Instant deployedAt(String commitHash) {
        return snapshot(commitHash).deployedAt();
    }

    /**
     * Returns a summary of all recorded deploys, ordered by deploy time descending.
     *
     * @return list of commit summaries
     */
    List<CommitSummary> listCommits();
}

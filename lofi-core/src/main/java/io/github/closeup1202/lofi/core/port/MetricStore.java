package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;

import java.util.List;

/**
 * Port for persisting and retrieving method metrics.
 *
 * <p>The default implementations are {@code SqliteMetricStore} (persistent, file-based)
 * and {@code InMemoryMetricStore} (ephemeral, for test/dev use).
 *
 * <p>TODO: multi-pod support — in a multi-pod environment, replace with a centralized
 * storage (external DB or dashboard) by providing an alternative implementation of this interface.
 */
public interface MetricStore {

    /**
     * Persists a single method metric for the current deploy.
     *
     * @param metric the metric to save
     */
    void save(MethodMetric metric);

    /**
     * Retrieves all metrics recorded for the given commit hash as a snapshot.
     *
     * @param commitHash the commit hash identifying the deploy
     * @return a snapshot containing all metrics and the inferred deploy time
     */
    DeploySnapshot snapshot(String commitHash);

    /**
     * Returns a summary of all recorded deploys, ordered by deploy time descending (most recent first).
     * Each entry contains the commit hash, first-seen time, and total metric count.
     *
     * @return list of commit summaries
     */
    List<CommitSummary> listCommits();

    /**
     * Persists a batch of metrics. Defaults to calling {@link #save} for each element;
     * implementations may override this for more efficient batch writes.
     *
     * @param metrics the list of metrics to save
     */
    default void saveAll(List<MethodMetric> metrics) {
        metrics.forEach(this::save);
    }
}

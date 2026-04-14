package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.MethodMetric;

import java.util.List;

/**
 * Write-only port for persisting method metrics.
 * Implemented by the collector-side store (AOP interceptor path).
 */
public interface WritableMetricStore {

    void ingest(String commitHash, List<MethodMetric> metrics);

    /**
     * Persists a single method metric for the current deploy.
     *
     * @param metric the metric to save
     */
    void save(MethodMetric metric);

    /**
     * Persists a batch of metrics.
     * Defaults to calling {@link #save} for each element;
     * implementations may override for more efficient batch writes.
     *
     * @param metrics the list of metrics to save
     */
    default void saveAll(List<MethodMetric> metrics) {
        metrics.forEach(this::save);
    }
}

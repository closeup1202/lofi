package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.MethodMetric;

import java.util.List;

/**
 * Write port for the AOP collector path.
 * Records a batch of measurements for the currently running deploy.
 * For the OTel ingest path see {@link IngestableStore}.
 */
public interface WritableMetricStore {

    /**
     * Persists a batch of metrics for the current deploy.
     *
     * @param metrics the list of metrics to save
     */
    void saveAll(List<MethodMetric> metrics);
}

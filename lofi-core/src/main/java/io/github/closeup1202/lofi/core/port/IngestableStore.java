package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.MethodMetric;

import java.util.List;

/**
 * Write port for the backend ingest path (OpenTelemetry pipeline).
 * Accepts a batch of metrics for a named commit from an external collector.
 * Separated from {@link WritableMetricStore} because the AOP collector writes
 * one metric at a time via {@link WritableMetricStore#save}, while the OTel
 * pipeline delivers pre-batched spans for an arbitrary commit hash.
 */
public interface IngestableStore {

    void ingest(String commitHash, List<MethodMetric> metrics);
}

package io.github.closeup1202.lofi.core.port;

/**
 * Full metric store port — combines read and write capabilities.
 *
 * <p>Collector-side implementations (SQLite, in-memory) implement this interface.
 * The lofi-backend uses only {@link ReadableMetricStore} since it never writes metrics directly.
 *
 * <p>TODO: multi-pod support — in a multi-pod environment, replace with a centralized
 * storage (external DB or dashboard) by providing an alternative implementation of this interface.
 */
public interface MetricStore extends ReadableMetricStore, WritableMetricStore {
}

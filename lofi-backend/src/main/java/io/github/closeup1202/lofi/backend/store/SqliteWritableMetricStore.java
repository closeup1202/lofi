package io.github.closeup1202.lofi.backend.store;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.WritableMetricStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class SqliteWritableMetricStore implements WritableMetricStore {

    private final JdbcTemplate jdbcTemplate;

    public SqliteWritableMetricStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void ingest(String commitHash, List<MethodMetric> metrics) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO method_metric (commit_hash, class_name, method_name, elapsed_ns, recorded_at) VALUES (?, ?, ?, ?, ?)",
                metrics.stream()
                        .map(m -> new Object[]{commitHash, m.className(), m.methodName(), m.elapsedNs(), m.recordedAt().toString()})
                        .toList()
        );
    }

    @Override
    public void save(MethodMetric metric) {
        throw new UnsupportedOperationException();
    }
}

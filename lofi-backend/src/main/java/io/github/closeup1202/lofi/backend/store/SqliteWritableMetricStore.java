package io.github.closeup1202.lofi.backend.store;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.WritableMetricStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class SqliteWritableMetricStore implements WritableMetricStore {

    private final JdbcTemplate jdbcTemplate;
    private final int retentionCommits;

    public SqliteWritableMetricStore(JdbcTemplate jdbcTemplate, int retentionCommits) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionCommits = retentionCommits;
    }

    @Override
    public void ingest(String commitHash, List<MethodMetric> metrics) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO method_metric (commit_hash, class_name, method_name, elapsed_ns, recorded_at) VALUES (?, ?, ?, ?, ?)",
                metrics.stream()
                        .map(m -> new Object[]{commitHash, m.className(), m.methodName(), m.elapsedNs(), m.recordedAt().toString()})
                        .toList()
        );
        evictOldCommits();
    }

    private void evictOldCommits() {
        jdbcTemplate.update(
                """
                        DELETE FROM method_metric WHERE commit_hash NOT IN (
                            SELECT commit_hash FROM (
                                SELECT DISTINCT commit_hash, MIN(recorded_at) AS first_seen
                                FROM method_metric
                                GROUP BY commit_hash
                                ORDER BY first_seen DESC
                                LIMIT ?
                            )
                        )
                        """,
                retentionCommits
        );
    }

    @Override
    public void save(MethodMetric metric) {
        throw new UnsupportedOperationException();
    }
}

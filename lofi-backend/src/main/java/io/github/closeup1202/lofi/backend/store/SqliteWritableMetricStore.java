package io.github.closeup1202.lofi.backend.store;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.IngestableStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

public class SqliteWritableMetricStore implements IngestableStore {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int retentionCommits;

    public SqliteWritableMetricStore(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, int retentionCommits) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.retentionCommits = retentionCommits;
    }

    @Override
    public void ingest(String commitHash, List<MethodMetric> metrics) {
        if (metrics.isEmpty()) return;
        // Probe before insert: if no row exists for this commit hash, the insert will introduce
        // a new commit and retention may need to evict the oldest. Otherwise the eviction is a
        // no-op scan we'd rather skip on the hot path. The probe uses idx_commit_hash and is
        // O(log n).
        boolean isNewCommit = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT NOT EXISTS(SELECT 1 FROM method_metric WHERE commit_hash = ?)",
                Boolean.class,
                commitHash
        ));

        // Wrap insert + (conditional) evict in a single transaction: one fsync per ingest,
        // and the insert/delete are observed atomically by readers.
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO method_metric (commit_hash, class_name, method_name, elapsed_ns, recorded_at) VALUES (?, ?, ?, ?, ?)",
                    metrics.stream()
                            .map(m -> new Object[]{commitHash, m.className(), m.methodName(), m.elapsedNs(), m.recordedAt().toString()})
                            .toList()
            );
            if (isNewCommit) {
                evictOldCommits();
            }
        });
    }

    private void evictOldCommits() {
        jdbcTemplate.update(
                """
                        DELETE FROM method_metric WHERE commit_hash NOT IN (
                            SELECT commit_hash FROM method_metric
                            GROUP BY commit_hash
                            ORDER BY MIN(recorded_at) DESC
                            LIMIT ?
                        )
                        """,
                retentionCommits
        );
    }
}

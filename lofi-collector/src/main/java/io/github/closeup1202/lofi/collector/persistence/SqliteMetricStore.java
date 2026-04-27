package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.domain.MethodStats;
import io.github.closeup1202.lofi.core.persistence.LofiSqlQueries;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import io.github.closeup1202.lofi.core.port.WritableMetricStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed implementation of {@link ReadableMetricStore} and {@link WritableMetricStore}.
 * Metrics are persisted to {@code ~/.lofi/metrics.db} and survive application restarts.
 * Uses a dedicated {@link JdbcTemplate} bean ({@code lofiJdbcTemplate}) to avoid
 * interfering with the application's own datasource.
 */
public class SqliteMetricStore implements ReadableMetricStore, WritableMetricStore {


    private final JdbcTemplate jdbcTemplate;
    private final DeployContext deployContext;
    private final TransactionTemplate transactionTemplate;

    public SqliteMetricStore(JdbcTemplate jdbcTemplate, DeployContext deployContext, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.deployContext = deployContext;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void saveAll(List<MethodMetric> metrics) {
        if (metrics.isEmpty()) return;
        String commitHash = deployContext.commitHash();
        // Wrap the batch in a single SQLite transaction so the underlying VFS performs
        // one fsync for the whole batch instead of one per row (default auto-commit).
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.batchUpdate(
                        "INSERT INTO method_metric (commit_hash, class_name, method_name, elapsed_ns, recorded_at) VALUES (?, ?, ?, ?, ?)",
                        metrics.stream()
                                .map(m -> new Object[]{commitHash, m.className(), m.methodName(), m.elapsedNs(), m.recordedAt().toString()})
                                .toList()
                )
        );
    }

    @Override
    public Map<String, MethodStats> statsByMethod(String commitHash) {
        return jdbcTemplate.query(
                LofiSqlQueries.STATS_BY_METHOD,
                rs -> {
                    Map<String, MethodStats> result = new HashMap<>();
                    while (rs.next()) {
                        String sig = rs.getString("class_name") + "." + rs.getString("method_name") + "()";
                        result.put(sig, new MethodStats(
                                rs.getDouble("avg_ns"),
                                rs.getDouble("p95_ns"),
                                rs.getDouble("p99_ns"),
                                rs.getInt("cnt")
                        ));
                    }
                    return result;
                },
                commitHash
        );
    }

    @Override
    public Instant deployedAt(String commitHash) {
        String raw = jdbcTemplate.queryForObject(
                "SELECT MIN(recorded_at) FROM method_metric WHERE commit_hash = ?",
                String.class, commitHash
        );
        return raw != null ? Instant.parse(raw) : Instant.EPOCH;
    }

    @Override
    public List<CommitSummary> listCommits() {
        return jdbcTemplate.query("""
                        SELECT commit_hash, MIN(recorded_at) AS deployed_at, COUNT(*) AS metric_count
                        FROM method_metric
                        GROUP BY commit_hash
                        ORDER BY MIN(recorded_at) DESC
                        """,
                (rs, rowNum) -> new CommitSummary(
                        rs.getString("commit_hash"),
                        Instant.parse(rs.getString("deployed_at")),
                        rs.getLong("metric_count")
                )
        );
    }

    @Override
    public DeploySnapshot snapshot(String commitHash) {
        List<MethodMetric> metrics = jdbcTemplate.query("""
                        SELECT class_name, method_name, elapsed_ns, recorded_at
                        FROM method_metric
                        WHERE commit_hash = ?
                        """,
                (rs, rowNum) -> new MethodMetric(
                        rs.getString("class_name"),
                        rs.getString("method_name"),
                        rs.getLong("elapsed_ns"),
                        Instant.parse(rs.getString("recorded_at"))
                ),
                commitHash
        );

        Instant deployedAt = metrics.stream()
                .map(MethodMetric::recordedAt)
                .min(Instant::compareTo)
                .orElse(Instant.EPOCH);

        return new DeploySnapshot(commitHash, deployedAt, metrics);
    }
}

package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

/**
 * SQLite-backed implementation of {@link MetricStore}.
 * Metrics are persisted to {@code ~/.lofi/metrics.db} and survive application restarts.
 * Uses a dedicated {@link JdbcTemplate} bean ({@code lofiJdbcTemplate}) to avoid
 * interfering with the application's own datasource.
 */
public class SqliteMetricStore implements MetricStore {

    private final JdbcTemplate jdbcTemplate;
    private final DeployContext deployContext;

    /**
     * @param jdbcTemplate  the lofi-dedicated JDBC template connected to the SQLite database
     * @param deployContext provides the current deploy's commit hash
     */
    public SqliteMetricStore(JdbcTemplate jdbcTemplate, DeployContext deployContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.deployContext = deployContext;
    }

    @Override
    public void ingest(String commitHash, List<MethodMetric> metrics) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void save(MethodMetric metric) {
        jdbcTemplate.update("""
                        INSERT INTO method_metric (commit_hash, class_name, method_name, elapsed_ns, recorded_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                deployContext.commitHash(),
                metric.className(),
                metric.methodName(),
                metric.elapsedNs(),
                metric.recordedAt().toString()
        );
    }

    @Override
    public void saveAll(List<MethodMetric> metrics) {
        String commitHash = deployContext.commitHash();
        jdbcTemplate.batchUpdate(
                "INSERT INTO method_metric (commit_hash, class_name, method_name, elapsed_ns, recorded_at) VALUES (?, ?, ?, ?, ?)",
                metrics.stream()
                        .map(m -> new Object[]{commitHash, m.className(), m.methodName(), m.elapsedNs(), m.recordedAt().toString()})
                        .toList()
        );
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

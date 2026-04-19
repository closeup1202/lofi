package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.domain.MethodStats;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed implementation of {@link MetricStore}.
 * Metrics are persisted to {@code ~/.lofi/metrics.db} and survive application restarts.
 * Uses a dedicated {@link JdbcTemplate} bean ({@code lofiJdbcTemplate}) to avoid
 * interfering with the application's own datasource.
 */
public class SqliteMetricStore implements MetricStore {

    private static final String STATS_BY_METHOD_SQL = """
            WITH ranked AS (
                SELECT
                    class_name,
                    method_name,
                    elapsed_ns,
                    COUNT(*) OVER (PARTITION BY class_name, method_name)                         AS total_cnt,
                    ROW_NUMBER() OVER (PARTITION BY class_name, method_name ORDER BY elapsed_ns) AS rn
                FROM method_metric
                WHERE commit_hash = ?
            ),
            p_targets AS (
                SELECT DISTINCT class_name, method_name, total_cnt,
                    (total_cnt * 95 + 99) / 100 AS p95_rn,
                    (total_cnt * 99 + 99) / 100 AS p99_rn
                FROM ranked
            )
            SELECT
                r.class_name,
                r.method_name,
                AVG(r.elapsed_ns)                                     AS avg_ns,
                t.total_cnt                                           AS cnt,
                MAX(CASE WHEN r.rn = t.p95_rn THEN r.elapsed_ns END) AS p95_ns,
                MAX(CASE WHEN r.rn = t.p99_rn THEN r.elapsed_ns END) AS p99_ns
            FROM ranked r
            JOIN p_targets t ON r.class_name = t.class_name AND r.method_name = t.method_name
            GROUP BY r.class_name, r.method_name
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DeployContext deployContext;

    public SqliteMetricStore(JdbcTemplate jdbcTemplate, DeployContext deployContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.deployContext = deployContext;
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
    public Map<String, MethodStats> statsByMethod(String commitHash) {
        return jdbcTemplate.query(
                STATS_BY_METHOD_SQL,
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

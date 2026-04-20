package io.github.closeup1202.lofi.backend.store;

import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.domain.MethodStats;
import io.github.closeup1202.lofi.core.persistence.LofiSqlQueries;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqliteReadableMetricStore implements ReadableMetricStore {


    private final JdbcTemplate jdbcTemplate;

    public SqliteReadableMetricStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}

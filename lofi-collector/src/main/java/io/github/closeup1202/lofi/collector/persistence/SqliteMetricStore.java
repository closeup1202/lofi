package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

public class SqliteMetricStore implements MetricStore {

    private final JdbcTemplate jdbcTemplate;
    private final DeployContext deployContext;

    public SqliteMetricStore(JdbcTemplate jdbcTemplate, DeployContext deployContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.deployContext = deployContext;
    }

    @Override
    public void save(MethodMetric metric) {
        jdbcTemplate.update("""
                        INSERT INTO method_metric (commit_hash, class_name, method_name, elapsed_ms, recorded_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                deployContext.getCommitHash(),
                metric.className(),
                metric.methodName(),
                metric.elapsedMs(),
                metric.recordedAt().toString()
        );
    }

    @Override
    public DeploySnapshot snapshot(String commitHash) {
        List<MethodMetric> metrics = jdbcTemplate.query("""
            SELECT class_name, method_name, elapsed_ms, recorded_at
            FROM method_metric
            WHERE commit_hash = ?
            """,
                (rs, rowNum) -> new MethodMetric(
                        rs.getString("class_name"),
                        rs.getString("method_name"),
                        rs.getLong("elapsed_ms"),
                        Instant.parse(rs.getString("recorded_at"))
                ),
                commitHash
        );
        return new DeploySnapshot(commitHash, Instant.now(), metrics);
    }
}

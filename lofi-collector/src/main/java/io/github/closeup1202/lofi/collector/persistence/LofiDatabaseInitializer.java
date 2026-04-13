package io.github.closeup1202.lofi.collector.persistence;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Initializes the lofi SQLite database on application startup.
 * Creates the {@code ~/.lofi} directory and {@code method_metric} table if they do not exist,
 * and purges deploys beyond the configured retention limit.
 */
public class LofiDatabaseInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;
    private final int retentionCommits;

    /**
     * @param jdbcTemplate     the lofi-dedicated JDBC template connected to the SQLite database
     * @param retentionCommits maximum number of recent deploys to retain; older rows are purged on startup
     */
    public LofiDatabaseInitializer(JdbcTemplate jdbcTemplate, int retentionCommits) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionCommits = retentionCommits;
    }

    @Override
    public void afterPropertiesSet() {
        createLofiDirectory();
        createTable();
        createIndex();
        purgeOldCommits();
    }

    private void createLofiDirectory() {
        try {
            Path lofiDir = Path.of(System.getProperty("user.home"), ".lofi");
            Files.createDirectories(lofiDir);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create ~/.lofi directory", e);
        }
    }

    private void createTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS method_metric (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    commit_hash TEXT    NOT NULL,
                    class_name  TEXT    NOT NULL,
                    method_name TEXT    NOT NULL,
                    elapsed_ms  INTEGER NOT NULL,
                    recorded_at TEXT    NOT NULL
                )
                """);
    }

    private void createIndex() {
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_commit_hash
                ON method_metric(commit_hash)
                """);
    }

    private void purgeOldCommits() {
        jdbcTemplate.update("""
                DELETE FROM method_metric
                WHERE commit_hash NOT IN (
                    SELECT commit_hash
                    FROM method_metric
                    GROUP BY commit_hash
                    ORDER BY MIN(recorded_at) DESC
                    LIMIT ?
                )
                """, retentionCommits);
    }
}

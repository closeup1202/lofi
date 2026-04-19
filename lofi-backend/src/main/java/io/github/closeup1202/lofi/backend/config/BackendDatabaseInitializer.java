package io.github.closeup1202.lofi.backend.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

public class BackendDatabaseInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;
    private final String dbPath;

    public BackendDatabaseInitializer(JdbcTemplate jdbcTemplate, String dbPath) {
        this.jdbcTemplate = jdbcTemplate;
        this.dbPath = dbPath;
    }

    @Override
    public void afterPropertiesSet() {
        createDirectory();
        createTable();
        migrateElapsedMsToNs();
        createIndex();
    }

    private void createDirectory() {
        try {
            Files.createDirectories(Path.of(dbPath).getParent());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create lofi database directory: " + dbPath, e);
        }
    }

    private void createTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS method_metric (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    commit_hash TEXT    NOT NULL,
                    class_name  TEXT    NOT NULL,
                    method_name TEXT    NOT NULL,
                    elapsed_ns  INTEGER NOT NULL,
                    recorded_at TEXT    NOT NULL
                )
                """);
    }

    private void migrateElapsedMsToNs() {
        boolean hasElapsedNs = jdbcTemplate.queryForList("PRAGMA table_info(method_metric)")
                .stream()
                .anyMatch(row -> "elapsed_ns".equals(row.get("name")));
        if (hasElapsedNs) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE method_metric RENAME COLUMN elapsed_ms TO elapsed_ns");
        jdbcTemplate.execute("UPDATE method_metric SET elapsed_ns = elapsed_ns * 1000000");
    }

    private void createIndex() {
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_commit_hash
                ON method_metric(commit_hash)
                """);
        // Covering index for the statsByMethod window-function query:
        // supports commit_hash filter, class/method partition, and elapsed_ns sort
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_commit_method_elapsed
                ON method_metric(commit_hash, class_name, method_name, elapsed_ns)
                """);
    }
}

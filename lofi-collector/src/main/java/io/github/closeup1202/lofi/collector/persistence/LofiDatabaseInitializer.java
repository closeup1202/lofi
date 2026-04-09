package io.github.closeup1202.lofi.collector.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

public class LofiDatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public LofiDatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initialize() {
        createLofiDirectory();
        createTable();
        createIndex();
    }

    private void createLofiDirectory() {
        try {
            Path lofiDir = Path.of(System.getProperty("user.home"), ".lofi");
            Files.createDirectories(lofiDir);
        } catch (Exception e) {
            throw new IllegalStateException("~/.lofi 디렉토리 생성 실패", e);
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
}
package io.github.closeup1202.lofi.backend.config;

import io.github.closeup1202.lofi.backend.service.LofiCommandService;
import io.github.closeup1202.lofi.backend.service.LofiQueryService;
import io.github.closeup1202.lofi.backend.store.SqliteReadableMetricStore;
import io.github.closeup1202.lofi.backend.store.SqliteWritableMetricStore;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.IngestableStore;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import io.github.closeup1202.lofi.core.service.DiffServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class BackendConfig {

    @Value("${lofi.backend.db-path}")
    private String dbPath;

    @Value("${lofi.backend.regression-threshold}")
    private double regressionThreshold;

    @Value("${lofi.backend.retention-commits}")
    private int retentionCommits;

    @Bean
    public DataSource lofiDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + dbPath);
        return ds;
    }

    @Bean
    public JdbcTemplate lofiJdbcTemplate(DataSource lofiDataSource) {
        return new JdbcTemplate(lofiDataSource);
    }

    @Bean
    public BackendDatabaseInitializer backendDatabaseInitializer(JdbcTemplate lofiJdbcTemplate) {
        return new BackendDatabaseInitializer(lofiJdbcTemplate, dbPath);
    }

    @Bean
    @DependsOn("backendDatabaseInitializer")
    public ReadableMetricStore readableMetricStore(JdbcTemplate lofiJdbcTemplate) {
        return new SqliteReadableMetricStore(lofiJdbcTemplate);
    }

    @Bean
    @DependsOn("backendDatabaseInitializer")
    public IngestableStore ingestableStore(JdbcTemplate lofiJdbcTemplate) {
        return new SqliteWritableMetricStore(lofiJdbcTemplate, retentionCommits);
    }

    @Bean
    public DiffService diffService(ReadableMetricStore readableMetricStore) {
        return new DiffServiceImpl(readableMetricStore, regressionThreshold);
    }

    @Bean
    public LofiQueryService lofiQueryService(ReadableMetricStore readableMetricStore, DiffService diffService) {
        return new LofiQueryService(readableMetricStore, diffService);
    }

    @Bean
    public LofiCommandService lofiCommandService(IngestableStore ingestableStore) {
        return new LofiCommandService(ingestableStore);
    }
}

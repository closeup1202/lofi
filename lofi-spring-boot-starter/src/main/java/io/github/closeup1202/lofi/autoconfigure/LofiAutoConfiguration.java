package io.github.closeup1202.lofi.autoconfigure;

import io.github.closeup1202.lofi.actuator.endpoint.LofiDiffEndpoint;
import io.github.closeup1202.lofi.actuator.endpoint.LofiEndpoint;
import io.github.closeup1202.lofi.actuator.service.DiffServiceImpl;
import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.collector.interceptor.LofiInterceptor;
import io.github.closeup1202.lofi.collector.persistence.InMemoryMetricStore;
import io.github.closeup1202.lofi.collector.persistence.LofiDatabaseInitializer;
import io.github.closeup1202.lofi.collector.persistence.MetricBuffer;
import io.github.closeup1202.lofi.collector.persistence.SqliteMetricStore;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
// @EnableScheduling is required for MetricBuffer (SchedulingConfigurer) to perform periodic flushes.
// Adding the lofi starter enables scheduling in the application context.
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.nio.file.Path;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(LofiProperties.class)
public class LofiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "lofiDataSource")
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "sqlite", matchIfMissing = true)
    public DataSource lofiDataSource() {
        Path dbPath = Path.of(System.getProperty("user.home"), ".lofi", "metrics.db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean(name = "lofiJdbcTemplate")
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "sqlite", matchIfMissing = true)
    public JdbcTemplate lofiJdbcTemplate(DataSource lofiDataSource) {
        return new JdbcTemplate(lofiDataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "sqlite", matchIfMissing = true)
    public LofiDatabaseInitializer lofiDatabaseInitializer(@Qualifier("lofiJdbcTemplate") JdbcTemplate lofiJdbcTemplate, LofiProperties properties) {
        return new LofiDatabaseInitializer(lofiJdbcTemplate, properties.retentionCommits());
    }

    @Bean
    @ConditionalOnMissingBean
    public DeployContext deployContext(LofiProperties properties) {
        return new DeployContext(properties.commitHash());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "sqlite", matchIfMissing = true)
    @DependsOn("lofiDatabaseInitializer")
    public MetricStore metricStore(@Qualifier("lofiJdbcTemplate") JdbcTemplate lofiJdbcTemplate, DeployContext deployContext) {
        return new SqliteMetricStore(lofiJdbcTemplate, deployContext);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "in-memory")
    public MetricStore inMemoryMetricStore(DeployContext deployContext, LofiProperties properties) {
        return new InMemoryMetricStore(deployContext, properties.retentionCommits());
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricBuffer metricBuffer(MetricStore metricStore, LofiProperties properties) {
        return new MetricBuffer(
                metricStore,
                properties.buffer().flushThreshold(),
                properties.buffer().flushDelayMs(),
                properties.buffer().queueCapacity()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public LofiInterceptor lofiInterceptor(MetricBuffer metricBuffer) {
        return new LofiInterceptor(metricBuffer);
    }

    @Bean
    @ConditionalOnMissingBean
    public DiffService diffService(MetricStore metricStore, LofiProperties properties) {
        return new DiffServiceImpl(metricStore, properties.regressionThreshold());
    }

    @Bean
    @ConditionalOnMissingBean
    public LofiEndpoint lofiEndpoint(MetricStore metricStore) {
        return new LofiEndpoint(metricStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public LofiDiffEndpoint lofiDiffEndpoint(DiffService diffService) {
        return new LofiDiffEndpoint(diffService);
    }
}
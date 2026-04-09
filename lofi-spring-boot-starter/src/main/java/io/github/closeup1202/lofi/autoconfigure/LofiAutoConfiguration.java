package io.github.closeup1202.lofi.autoconfigure;

import io.github.closeup1202.lofi.actuator.endpoint.LofiDiffEndpoint;
import io.github.closeup1202.lofi.actuator.endpoint.LofiEndpoint;
import io.github.closeup1202.lofi.actuator.service.DiffServiceImpl;
import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.collector.interceptor.LofiInterceptor;
import io.github.closeup1202.lofi.collector.persistence.LofiDatabaseInitializer;
import io.github.closeup1202.lofi.collector.persistence.MetricBuffer;
import io.github.closeup1202.lofi.collector.persistence.SqliteMetricStore;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.nio.file.Path;

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(LofiProperties.class)
public class LofiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "lofiDataSource")
    public DataSource lofiDataSource() {
        Path dbPath = Path.of(System.getProperty("user.home"), ".lofi", "metrics.db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean(name = "lofiJdbcTemplate")
    public JdbcTemplate lofiJdbcTemplate(DataSource lofiDataSource) {
        return new JdbcTemplate(lofiDataSource);
    }

    @Bean
    public LofiDatabaseInitializer lofiDatabaseInitializer(JdbcTemplate lofiJdbcTemplate) {
        LofiDatabaseInitializer initializer = new LofiDatabaseInitializer(lofiJdbcTemplate);
        initializer.initialize();
        return initializer;
    }

    @Bean
    @ConditionalOnMissingBean
    @DependsOn("lofiDatabaseInitializer")
    public DeployContext deployContext(LofiProperties properties) {
        return new DeployContext(properties.getCommitHash());
    }

    @Bean
    @ConditionalOnMissingBean
    @DependsOn("lofiDatabaseInitializer")
    public MetricStore metricStore(JdbcTemplate lofiJdbcTemplate, DeployContext deployContext) {
        return new SqliteMetricStore(lofiJdbcTemplate, deployContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricBuffer metricBuffer(MetricStore metricStore, LofiProperties properties) {
        return new MetricBuffer(
                metricStore,
                properties.getBuffer().getFlushThreshold(),
                properties.getBuffer().getFlushDelayMs()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public LofiInterceptor lofiInterceptor(MetricBuffer metricBuffer) {
        return new LofiInterceptor(metricBuffer);
    }

    @Bean
    @ConditionalOnMissingBean
    public DiffService diffService(MetricStore metricStore) {
        return new DiffServiceImpl(metricStore);
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
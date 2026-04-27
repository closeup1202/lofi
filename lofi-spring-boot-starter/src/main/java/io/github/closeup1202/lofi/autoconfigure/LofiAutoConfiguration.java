package io.github.closeup1202.lofi.autoconfigure;

import io.github.closeup1202.lofi.actuator.endpoint.LofiEndpoint;
import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.collector.interceptor.LofiInterceptor;
import io.github.closeup1202.lofi.collector.persistence.InMemoryMetricStore;
import io.github.closeup1202.lofi.collector.persistence.LofiDatabaseInitializer;
import io.github.closeup1202.lofi.collector.persistence.MetricBuffer;
import io.github.closeup1202.lofi.collector.persistence.SqliteMetricStore;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import io.github.closeup1202.lofi.core.port.WritableMetricStore;
import io.github.closeup1202.lofi.core.service.DiffServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(LofiProperties.class)
public class LofiAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LofiAutoConfiguration.class);

    /**
     * Creates a dedicated JdbcTemplate backed by lofi's own SQLite datasource.
     * The SQLite DataSource is intentionally created inline and never registered as a
     * separate DataSource bean, so it does not interfere with the application's primary
     * DataSource or Spring Boot's JPA auto-configuration.
     */
    @Bean
    @ConditionalOnMissingBean(name = "lofiJdbcTemplate")
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "sqlite", matchIfMissing = true)
    public JdbcTemplate lofiJdbcTemplate() {
        Path dbPath = Path.of(System.getProperty("user.home"), ".lofi", "metrics.db");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        // WAL allows readers to proceed concurrently with the single writer;
        // busy_timeout (ms) tells SQLite to wait/retry instead of immediately
        // throwing SQLITE_BUSY when the lock is contended (e.g. lofi-flush daemon
        // racing with an overflow flush from a request thread).
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath() + "?journal_mode=WAL&busy_timeout=5000");
        return new JdbcTemplate(dataSource);
    }

    /**
     * Lofi-private TransactionTemplate so {@link SqliteMetricStore#saveAll} can wrap each
     * batch in a single SQLite transaction (one fsync per flush instead of per row).
     * Bound to the lofi JdbcTemplate's DataSource and never registered as a primary
     * transaction manager, so it does not participate in the application's @Transactional
     * boundaries.
     */
    @Bean
    @ConditionalOnMissingBean(name = "lofiTransactionTemplate")
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "sqlite", matchIfMissing = true)
    public TransactionTemplate lofiTransactionTemplate(@Qualifier("lofiJdbcTemplate") JdbcTemplate lofiJdbcTemplate) {
        PlatformTransactionManager tm = new DataSourceTransactionManager(lofiJdbcTemplate.getDataSource());
        return new TransactionTemplate(tm);
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
    @ConditionalOnMissingBean({ReadableMetricStore.class, WritableMetricStore.class})
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "sqlite", matchIfMissing = true)
    @DependsOn("lofiDatabaseInitializer")
    public SqliteMetricStore metricStore(
            @Qualifier("lofiJdbcTemplate") JdbcTemplate lofiJdbcTemplate,
            DeployContext deployContext,
            @Qualifier("lofiTransactionTemplate") TransactionTemplate lofiTransactionTemplate
    ) {
        return new SqliteMetricStore(lofiJdbcTemplate, deployContext, lofiTransactionTemplate);
    }

    @Bean
    @ConditionalOnMissingBean({ReadableMetricStore.class, WritableMetricStore.class})
    @ConditionalOnProperty(name = "lofi.store-type", havingValue = "in-memory")
    public InMemoryMetricStore inMemoryMetricStore(DeployContext deployContext, LofiProperties properties) {
        return new InMemoryMetricStore(deployContext, properties.retentionCommits());
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricBuffer metricBuffer(WritableMetricStore writableMetricStore, LofiProperties properties) {
        return new MetricBuffer(
                writableMetricStore,
                properties.buffer().flushThreshold(),
                properties.buffer().flushDelayMs(),
                properties.buffer().queueCapacity()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public LofiInterceptor lofiInterceptor(MetricBuffer metricBuffer, LofiProperties properties) {
        return new LofiInterceptor(metricBuffer, properties.excludePackages());
    }

    @Bean
    @ConditionalOnMissingBean
    public DiffService diffService(ReadableMetricStore readableMetricStore, LofiProperties properties) {
        return new DiffServiceImpl(readableMetricStore, properties.regressionThreshold());
    }

    @Bean
    @ConditionalOnMissingBean
    public LofiEndpoint lofiEndpoint(ReadableMetricStore readableMetricStore, DiffService diffService) {
        return new LofiEndpoint(readableMetricStore, diffService);
    }

    @Bean
    public ApplicationRunner lofiStartupLogger(DeployContext deployContext, LofiProperties properties) {
        return args -> log.info(
                "[LO-FI] Monitoring active — commit: {} | store: {} | regression-threshold: {}",
                deployContext.commitHash(),
                properties.storeType(),
                properties.regressionThreshold()
        );
    }
}
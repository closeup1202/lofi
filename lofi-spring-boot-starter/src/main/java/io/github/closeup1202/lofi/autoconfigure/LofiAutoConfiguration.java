package io.github.closeup1202.lofi.autoconfigure;

import io.github.closeup1202.lofi.actuator.endpoint.LofiDiffEndpoint;
import io.github.closeup1202.lofi.actuator.endpoint.LofiEndpoint;
import io.github.closeup1202.lofi.actuator.service.DiffServiceImpl;
import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.collector.interceptor.LofiInterceptor;
import io.github.closeup1202.lofi.collector.persistence.MetricBuffer;
import io.github.closeup1202.lofi.collector.persistence.SqliteMetricStore;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(LofiProperties.class)
public class LofiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeployContext deployContext(LofiProperties properties) {
        return new DeployContext(properties.getCommitHash());
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricStore metricStore(JdbcTemplate jdbcTemplate, DeployContext deployContext) {
        return new SqliteMetricStore(jdbcTemplate, deployContext);
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
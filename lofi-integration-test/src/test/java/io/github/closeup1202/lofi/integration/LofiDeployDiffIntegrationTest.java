package io.github.closeup1202.lofi.integration;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.collector.persistence.SqliteMetricStore;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;
import io.github.closeup1202.lofi.integration.fixture.TestApplication;
import io.github.closeup1202.lofi.integration.fixture.TestService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {TestApplication.class, LofiDeployDiffIntegrationTest.TestConfig.class})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LofiDeployDiffIntegrationTest {

    static final String DB_PATH = "/tmp/lofi-test.db";
    static final String BASE_COMMIT = "base-commit-001";
    static final String HEAD_COMMIT = "head-commit-002";

    // 현재 활성 커밋 해시를 테스트 간에 공유
    static final AtomicReference<String> activeCommit = new AtomicReference<>(BASE_COMMIT);

    @TestConfiguration
    static class TestConfig {

        @Bean
        public DataSource lofiDataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.sqlite.JDBC");
            dataSource.setUrl("jdbc:sqlite:" + DB_PATH);
            return dataSource;
        }

        @Bean
        @Primary
        public MetricStore testMetricStore(JdbcTemplate lofiJdbcTemplate) {
            // activeCommit을 참조하는 동적 DeployContext
            return new SqliteMetricStore(
                    lofiJdbcTemplate,
                    new DeployContext(activeCommit.get())
            ) {
                @Override
                public void save(MethodMetric metric) {
                    // 저장 시점의 activeCommit을 반영
                    SqliteMetricStore store = new SqliteMetricStore(
                            lofiJdbcTemplate,
                            new DeployContext(activeCommit.get())
                    );
                    store.save(metric);
                }
            };
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("lofi.commit-hash", () -> BASE_COMMIT);
        registry.add("lofi.buffer.flush-threshold", () -> 1);
        registry.add("lofi.buffer.flush-delay-ms", () -> 100);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestService testService;

    @Test
    @Order(1)
    void 배포_A_메트릭_수집() throws Exception {
        activeCommit.set(BASE_COMMIT);
        TestService.slowDelayMs = 10;

        testService.slowMethod();
        testService.slowMethod();
        testService.slowMethod();

        Thread.sleep(200);

        mockMvc.perform(get("/actuator/lofi/" + BASE_COMMIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitHash").value(BASE_COMMIT))
                .andExpect(jsonPath("$.metrics.length()").value(3));
    }

    @Test
    @Order(2)
    void 배포_B_메트릭_수집() throws Exception {
        activeCommit.set(HEAD_COMMIT);
        TestService.slowDelayMs = 100;

        testService.slowMethod();
        testService.slowMethod();
        testService.slowMethod();

        Thread.sleep(200);

        mockMvc.perform(get("/actuator/lofi/" + HEAD_COMMIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitHash").value(HEAD_COMMIT))
                .andExpect(jsonPath("$.metrics.length()").value(3));
    }

    @Test
    @Order(3)
    void 배포_A_B_diff_regression_감지() throws Exception {
        mockMvc.perform(get("/actuator/lofi-diff")
                        .param("base", BASE_COMMIT)
                        .param("head", HEAD_COMMIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCommit").value(BASE_COMMIT))
                .andExpect(jsonPath("$.headCommit").value(HEAD_COMMIT))
                .andExpect(jsonPath("$.diffs").isArray())
                .andExpect(jsonPath(
                        "$.diffs[?(@.signature == 'TestService.slowMethod()' && @.regressed == true)]"
                ).exists());
    }

    @AfterAll
    static void cleanup() {
        new File(DB_PATH).delete();
    }
}

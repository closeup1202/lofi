package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.domain.MethodStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SqliteMetricStoreTest {

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite::memory:");
        jdbcTemplate = new JdbcTemplate(ds);
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
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_commit_method_elapsed
                ON method_metric(commit_hash, class_name, method_name, elapsed_ns)
                """);
    }

    private SqliteMetricStore storeFor(String commitHash) {
        return new SqliteMetricStore(jdbcTemplate, new DeployContext(commitHash));
    }

    private void save(String commitHash, String className, String methodName, long elapsedNs) {
        storeFor(commitHash).save(new MethodMetric(className, methodName, elapsedNs, Instant.now()));
    }

    @Test
    void statsByMethod_shouldComputeCorrectAvgAndCount() {
        save("commit-a", "TestClass", "testMethod", 1_000_000L);
        save("commit-a", "TestClass", "testMethod", 2_000_000L);
        save("commit-a", "TestClass", "testMethod", 3_000_000L);

        Map<String, MethodStats> stats = storeFor("commit-a").statsByMethod("commit-a");

        MethodStats s = stats.get("TestClass.testMethod()");
        assertThat(s).isNotNull();
        assertThat(s.count()).isEqualTo(3);
        assertThat(s.avgNs()).isCloseTo(2_000_000.0, within(1.0));
    }

    @Test
    void statsByMethod_shouldComputePercentiles_forSingleSample() {
        save("commit-b", "TestClass", "testMethod", 5_000_000L);

        Map<String, MethodStats> stats = storeFor("commit-b").statsByMethod("commit-b");

        MethodStats s = stats.get("TestClass.testMethod()");
        assertThat(s.p95Ns()).isEqualTo(5_000_000.0);
        assertThat(s.p99Ns()).isEqualTo(5_000_000.0);
    }

    @Test
    void statsByMethod_shouldComputePercentiles_for100Samples() {
        // values 1..100 ns
        for (long ns = 1; ns <= 100; ns++) {
            save("commit-c", "TestClass", "testMethod", ns);
        }

        Map<String, MethodStats> stats = storeFor("commit-c").statsByMethod("commit-c");

        MethodStats s = stats.get("TestClass.testMethod()");
        assertThat(s.count()).isEqualTo(100);
        assertThat(s.avgNs()).isCloseTo(50.5, within(1.0));
        assertThat(s.p95Ns()).isEqualTo(95.0);
        assertThat(s.p99Ns()).isEqualTo(99.0);
    }

    @Test
    void statsByMethod_shouldHandleMultipleMethods() {
        save("commit-d", "TestClass", "methodA", 1_000_000L);
        save("commit-d", "TestClass", "methodB", 2_000_000L);
        save("commit-d", "TestClass", "methodB", 4_000_000L);

        Map<String, MethodStats> stats = storeFor("commit-d").statsByMethod("commit-d");

        assertThat(stats).containsKeys("TestClass.methodA()", "TestClass.methodB()");
        assertThat(stats.get("TestClass.methodA()").count()).isEqualTo(1);
        assertThat(stats.get("TestClass.methodB()").count()).isEqualTo(2);
        assertThat(stats.get("TestClass.methodB()").avgNs()).isCloseTo(3_000_000.0, within(1.0));
    }

    @Test
    void statsByMethod_shouldReturnEmpty_whenNoMetricsForCommit() {
        save("other-commit", "TestClass", "testMethod", 1_000_000L);

        Map<String, MethodStats> stats = storeFor("commit-e").statsByMethod("commit-e");

        assertThat(stats).isEmpty();
    }

    @Test
    void statsByMethod_shouldNotReturnMetricsFromOtherCommits() {
        save("commit-f", "TestClass", "testMethod", 1_000_000L);
        save("commit-g", "TestClass", "testMethod", 999_000_000L);

        Map<String, MethodStats> stats = storeFor("commit-f").statsByMethod("commit-f");

        assertThat(stats.get("TestClass.testMethod()").avgNs()).isCloseTo(1_000_000.0, within(1.0));
    }
}

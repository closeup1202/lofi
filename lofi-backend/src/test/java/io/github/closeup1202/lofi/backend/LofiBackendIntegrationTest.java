package io.github.closeup1202.lofi.backend;

import io.github.closeup1202.lofi.backend.api.request.IngestRequest;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LofiBackendIntegrationTest {

    static final String DB_PATH = Path.of(System.getProperty("java.io.tmpdir"), "lofi-backend-test.db").toString();
    static final String BASE_COMMIT = "base-backend-001";
    static final String HEAD_COMMIT = "head-backend-002";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("lofi.backend.db-path", () -> DB_PATH);
        registry.add("lofi.backend.regression-threshold", () -> "0.2");
        registry.add("lofi.backend.retention-commits", () -> "50");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @Order(1)
    void ingest_shouldReturn201_forBaseCommit() {
        List<MethodMetric> metrics = List.of(
                new MethodMetric("TestService", "process", 10_000_000L, Instant.now()),
                new MethodMetric("TestService", "process", 11_000_000L, Instant.now()),
                new MethodMetric("TestService", "process", 12_000_000L, Instant.now())
        );
        IngestRequest request = new IngestRequest(BASE_COMMIT, metrics);

        ResponseEntity<Void> response = restTemplate.postForEntity("/lofi/ingest", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(2)
    void ingest_shouldReturn201_forHeadCommitWithRegression() {
        List<MethodMetric> metrics = List.of(
                new MethodMetric("TestService", "process", 100_000_000L, Instant.now()),
                new MethodMetric("TestService", "process", 110_000_000L, Instant.now()),
                new MethodMetric("TestService", "process", 120_000_000L, Instant.now())
        );
        IngestRequest request = new IngestRequest(HEAD_COMMIT, metrics);

        ResponseEntity<Void> response = restTemplate.postForEntity("/lofi/ingest", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(3)
    void commits_shouldReturnBothCommits() {
        ResponseEntity<List> response = restTemplate.getForEntity("/lofi", List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @Order(4)
    void snapshot_shouldReturn200_forKnownCommit() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/lofi/" + BASE_COMMIT, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("commitHash");
        assertThat(response.getBody().get("commitHash")).isEqualTo(BASE_COMMIT);
        assertThat(response.getBody()).containsKey("methods");
        assertThat((Map<?, ?>) response.getBody().get("methods")).isNotEmpty();
    }

    @Test
    @Order(5)
    void snapshot_shouldReturn404_forUnknownCommit() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/lofi/nonexistent", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(6)
    void diff_shouldDetectRegression() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/lofi/diff?base=" + BASE_COMMIT + "&head=" + HEAD_COMMIT,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<?, ?> body = response.getBody();
        assertThat(body.get("baseCommit")).isEqualTo(BASE_COMMIT);
        assertThat(body.get("headCommit")).isEqualTo(HEAD_COMMIT);

        List<?> diffs = (List<?>) body.get("diffs");
        assertThat(diffs).isNotEmpty();
        boolean hasRegression = diffs.stream()
                .anyMatch(d -> Boolean.TRUE.equals(((Map<?, ?>) d).get("regressed")));
        assertThat(hasRegression).isTrue();
    }

    @Test
    @Order(7)
    void ingest_shouldReturn400_whenCommitHashIsBlank() {
        IngestRequest request = new IngestRequest("", List.of(
                new MethodMetric("TestService", "process", 10_000_000L, Instant.now())
        ));

        ResponseEntity<Map> response = restTemplate.postForEntity("/lofi/ingest", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @AfterAll
    static void cleanup() {
        new File(DB_PATH).delete();
    }
}

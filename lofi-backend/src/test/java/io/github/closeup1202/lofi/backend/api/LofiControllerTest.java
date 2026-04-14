package io.github.closeup1202.lofi.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.closeup1202.lofi.backend.api.exception.CommitNotFoundException;
import io.github.closeup1202.lofi.backend.api.request.IngestRequest;
import io.github.closeup1202.lofi.backend.service.LofiCommandService;
import io.github.closeup1202.lofi.backend.service.LofiQueryService;
import io.github.closeup1202.lofi.core.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LofiController.class)
class LofiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LofiQueryService queryService;

    @MockitoBean
    private LofiCommandService commandService;

    @Test
    void commits_shouldReturn200WithList() throws Exception {
        given(queryService.listCommits()).willReturn(List.of(
                new CommitSummary("a3f9c1", Instant.parse("2026-04-14T04:10:00Z"), 6)
        ));

        mockMvc.perform(get("/lofi/commits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commitHash").value("a3f9c1"))
                .andExpect(jsonPath("$[0].metricCount").value(6));
    }

    @Test
    void snapshot_shouldReturn200_whenCommitExists() throws Exception {
        String commitHash = "a3f9c1";
        DeploySnapshot snapshot = new DeploySnapshot(commitHash, Instant.parse("2026-04-14T04:10:00Z"),
                List.of(new MethodMetric("TestClass", "testMethod", 2_000_000L, Instant.now())));
        given(queryService.snapshot(commitHash)).willReturn(snapshot);

        mockMvc.perform(get("/lofi/snapshot/{commitHash}", commitHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitHash").value(commitHash))
                .andExpect(jsonPath("$.metrics[0].elapsedMs").value(2.0))
                .andExpect(jsonPath("$.metrics[0].elapsedNs").doesNotExist());
    }

    @Test
    void snapshot_shouldReturn404_whenCommitNotFound() throws Exception {
        String commitHash = "unknown";
        given(queryService.snapshot(commitHash)).willThrow(new CommitNotFoundException(commitHash));

        mockMvc.perform(get("/lofi/snapshot/{commitHash}", commitHash))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No metrics found for commit: " + commitHash));
    }

    @Test
    void diff_shouldReturn200() throws Exception {
        String base = "a3f9c1";
        String head = "d82e04";
        given(queryService.diff(base, head)).willReturn(new DiffResult(base, head, List.of()));

        mockMvc.perform(get("/lofi/diff").param("base", base).param("head", head))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCommit").value(base))
                .andExpect(jsonPath("$.headCommit").value(head));
    }

    @Test
    void diff_shouldConvertNsToMs() throws Exception {
        String base = "a3f9c1";
        String head = "d82e04";
        MethodDiff methodDiff = new MethodDiff("TestClass.testMethod", 1_000_000.0, 3_000_000.0, 2_000_000.0, true);
        given(queryService.diff(base, head)).willReturn(new DiffResult(base, head, List.of(methodDiff)));

        mockMvc.perform(get("/lofi/diff").param("base", base).param("head", head))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diffs[0].baseMs").value(1.0))
                .andExpect(jsonPath("$.diffs[0].headMs").value(3.0))
                .andExpect(jsonPath("$.diffs[0].deltaMs").value(2.0))
                .andExpect(jsonPath("$.diffs[0].regressed").value(true))
                .andExpect(jsonPath("$.diffs[0].baseNs").doesNotExist())
                .andExpect(jsonPath("$.diffs[0].headNs").doesNotExist());
    }

    @Test
    void ingest_shouldReturn201() throws Exception {
        List<MethodMetric> metrics = List.of(
                new MethodMetric("TestClass", "testMethod", 1_000_000L, Instant.now())
        );
        IngestRequest request = new IngestRequest("a3f9c1", metrics);
        doNothing().when(commandService).ingest(request);

        mockMvc.perform(post("/lofi/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void ingest_shouldReturn400_whenCommitHashIsBlank() throws Exception {
        IngestRequest request = new IngestRequest("", List.of(
                new MethodMetric("TestClass", "testMethod", 1_000_000L, Instant.now())
        ));

        mockMvc.perform(post("/lofi/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingest_shouldReturn400_whenMetricsIsEmpty() throws Exception {
        IngestRequest request = new IngestRequest("a3f9c1", List.of());

        mockMvc.perform(post("/lofi/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

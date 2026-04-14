package io.github.closeup1202.lofi.backend.service;

import io.github.closeup1202.lofi.backend.api.exception.CommitNotFoundException;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LofiQueryServiceTest {

    @Mock
    private ReadableMetricStore metricStore;

    @Mock
    private DiffService diffService;

    private LofiQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new LofiQueryService(metricStore, diffService);
    }

    @Test
    void snapshot_shouldReturnSnapshot_whenMetricsExist() {
        String commitHash = "a3f9c1";
        DeploySnapshot snapshot = new DeploySnapshot(commitHash, Instant.now(),
                List.of(new MethodMetric("TestClass", "testMethod", 1_000_000L, Instant.now())));
        given(metricStore.snapshot(commitHash)).willReturn(snapshot);

        DeploySnapshot result = queryService.snapshot(commitHash);

        assertThat(result.commitHash()).isEqualTo(commitHash);
        assertThat(result.metrics()).hasSize(1);
    }

    @Test
    void snapshot_shouldThrowCommitNotFoundException_whenNoMetrics() {
        String commitHash = "unknown";
        given(metricStore.snapshot(commitHash))
                .willReturn(new DeploySnapshot(commitHash, Instant.EPOCH, List.of()));

        assertThatThrownBy(() -> queryService.snapshot(commitHash))
                .isInstanceOf(CommitNotFoundException.class)
                .hasMessageContaining(commitHash);
    }

    @Test
    void diff_shouldDelegateToDiffService() {
        String base = "a3f9c1";
        String head = "d82e04";
        DiffResult expected = new DiffResult(base, head, List.of());
        given(diffService.diff(base, head)).willReturn(expected);

        DiffResult result = queryService.diff(base, head);

        assertThat(result.baseCommit()).isEqualTo(base);
        assertThat(result.headCommit()).isEqualTo(head);
    }
}

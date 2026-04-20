package io.github.closeup1202.lofi.backend.service;

import io.github.closeup1202.lofi.backend.api.exception.CommitNotFoundException;
import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.domain.MethodStats;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import io.github.closeup1202.lofi.core.view.DeploySnapshotView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    void snapshot_shouldReturnView_whenMetricsExist() {
        String commitHash = "a3f9c1";
        Map<String, MethodStats> stats = Map.of(
                "TestClass.testMethod()", new MethodStats(1_000_000.0, 1_500_000.0, 1_800_000.0, 3)
        );
        given(metricStore.statsByMethod(commitHash)).willReturn(stats);
        given(metricStore.deployedAt(commitHash)).willReturn(Instant.parse("2026-04-14T04:10:00Z"));

        DeploySnapshotView result = queryService.snapshot(commitHash);

        assertThat(result.commitHash()).isEqualTo(commitHash);
        assertThat(result.methods()).containsKey("TestClass.testMethod()");
        assertThat(result.methods().get("TestClass.testMethod()").avgMs()).isEqualTo(1.0);
    }

    @Test
    void snapshot_shouldThrowCommitNotFoundException_whenNoMetrics() {
        String commitHash = "unknown";
        given(metricStore.statsByMethod(commitHash)).willReturn(Map.of());

        assertThatThrownBy(() -> queryService.snapshot(commitHash))
                .isInstanceOf(CommitNotFoundException.class)
                .hasMessageContaining(commitHash);
    }

    @Test
    void diff_shouldDelegateToDiffService() {
        String base = "a3f9c1";
        String head = "d82e04";
        DiffResult expected = new DiffResult(base, head, List.of(), 0.2);
        given(diffService.diff(base, head)).willReturn(expected);

        DiffResult result = queryService.diff(base, head);

        assertThat(result.baseCommit()).isEqualTo(base);
        assertThat(result.headCommit()).isEqualTo(head);
    }
}

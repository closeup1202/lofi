package io.github.closeup1202.lofi.actuator.service;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.domain.MethodDiff;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DiffServiceImplTest {

    @Mock
    private MetricStore metricStore;

    private DiffService diffService;

    private final String baseCommit = "testBaseCommit";
    private final String headCommit = "testHeadCommit";

    @BeforeEach
    void setUp() {
        diffService = new DiffServiceImpl(metricStore, 0.2);
    }

    @NonNull
    private static DeploySnapshot getDeploySnapshot(long elapsedNs, String commitHash) {
        String className = "testClass";
        String methodName = "testMethod";
        MethodMetric metric = new MethodMetric(className, methodName, elapsedNs, Instant.now());
        return new DeploySnapshot(commitHash, metric.recordedAt(), List.of(metric));
    }

    @Test
    void shouldMarkRegressedWhenDeltaExceedsThreshold() {
        // given
        long baseElapsedNs = 10L;
        long headElapsedNs = 100L;

        DeploySnapshot baseDeploySnapshot = getDeploySnapshot(baseElapsedNs, baseCommit);
        DeploySnapshot headDeploySnapshot = getDeploySnapshot(headElapsedNs, headCommit);

        given(metricStore.snapshot(baseCommit)).willReturn(baseDeploySnapshot);
        given(metricStore.snapshot(headCommit)).willReturn(headDeploySnapshot);

        // when
        DiffResult diffResult = diffService.diff(baseCommit, headCommit);
        List<MethodDiff> diffs = diffResult.diffs();

        // then
        assertThat(diffResult).isNotNull();
        assertThat(diffs).isNotEmpty();
        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).baseNs()).isEqualTo((double) baseElapsedNs);
        assertThat(diffs.get(0).headNs()).isEqualTo((double) headElapsedNs);
        assertThat(diffs.get(0).deltaNs()).isEqualTo(90.0);
        assertThat(diffs.get(0).regressed()).isTrue();
    }

    @Test
    void shouldNotMarkRegressedWhenDeltaIsWithinThreshold() {
        // given
        long baseElapsedNs = 100L;
        long headElapsedNs = 119L;

        DeploySnapshot baseDeploySnapshot = getDeploySnapshot(baseElapsedNs, baseCommit);
        DeploySnapshot headDeploySnapshot = getDeploySnapshot(headElapsedNs, headCommit);

        given(metricStore.snapshot(baseCommit)).willReturn(baseDeploySnapshot);
        given(metricStore.snapshot(headCommit)).willReturn(headDeploySnapshot);

        // when
        DiffResult diffResult = diffService.diff(baseCommit, headCommit);
        List<MethodDiff> diffs = diffResult.diffs();

        // then
        assertThat(diffs.get(0).deltaNs()).isEqualTo(19.0);
        assertThat(diffs.get(0).regressed()).isFalse();
    }

    @Test
    void shouldNotMarkRegressedWhenDeltaIsWithinExactlyThreshold() {
        // given
        long baseElapsedNs = 100L;
        long headElapsedNs = 120L;

        DeploySnapshot baseDeploySnapshot = getDeploySnapshot(baseElapsedNs, baseCommit);
        DeploySnapshot headDeploySnapshot = getDeploySnapshot(headElapsedNs, headCommit);

        given(metricStore.snapshot(baseCommit)).willReturn(baseDeploySnapshot);
        given(metricStore.snapshot(headCommit)).willReturn(headDeploySnapshot);

        // when
        DiffResult diffResult = diffService.diff(baseCommit, headCommit);
        List<MethodDiff> diffs = diffResult.diffs();

        // then
        assertThat(diffs.get(0).deltaNs()).isEqualTo(20.0);
        assertThat(diffs.get(0).regressed()).isFalse();
    }

    @Test
    void shouldMarkRegressedWhenBaseElapsedNsIsZeroAndHeadIsPositive() {
        // given
        long baseElapsedNs = 0L;
        long headElapsedNs = 100L;

        DeploySnapshot baseDeploySnapshot = getDeploySnapshot(baseElapsedNs, baseCommit);
        DeploySnapshot headDeploySnapshot = getDeploySnapshot(headElapsedNs, headCommit);

        given(metricStore.snapshot(baseCommit)).willReturn(baseDeploySnapshot);
        given(metricStore.snapshot(headCommit)).willReturn(headDeploySnapshot);

        // when
        DiffResult diffResult = diffService.diff(baseCommit, headCommit);
        List<MethodDiff> diffs = diffResult.diffs();

        // then
        assertThat(diffs.get(0).deltaNs()).isEqualTo(100.0);
        assertThat(diffs.get(0).regressed()).isTrue();
    }
}
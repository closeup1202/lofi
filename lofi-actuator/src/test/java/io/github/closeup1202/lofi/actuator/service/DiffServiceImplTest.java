package io.github.closeup1202.lofi.actuator.service;

import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.domain.MethodDiff;
import io.github.closeup1202.lofi.core.domain.MethodStats;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import io.github.closeup1202.lofi.core.service.DiffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DiffServiceImplTest {

    @Mock
    private ReadableMetricStore metricStore;

    private DiffService diffService;

    private final String baseCommit = "testBaseCommit";
    private final String headCommit = "testHeadCommit";
    private static final String SIG = "testClass.testMethod()";

    @BeforeEach
    void setUp() {
        diffService = new DiffServiceImpl(metricStore, 0.2);
    }

    private static MethodStats stats(double avgNs) {
        return new MethodStats(avgNs, avgNs, avgNs, 1);
    }

    @Test
    void shouldMarkRegressedWhenDeltaExceedsThreshold() {
        given(metricStore.statsByMethod(baseCommit)).willReturn(Map.of(SIG, stats(10.0)));
        given(metricStore.statsByMethod(headCommit)).willReturn(Map.of(SIG, stats(100.0)));

        DiffResult diffResult = diffService.diff(baseCommit, headCommit);
        List<MethodDiff> diffs = diffResult.diffs();

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).baseNs()).isEqualTo(10.0);
        assertThat(diffs.get(0).headNs()).isEqualTo(100.0);
        assertThat(diffs.get(0).deltaNs()).isEqualTo(90.0);
        assertThat(diffs.get(0).regressed()).isTrue();
    }

    @Test
    void shouldNotMarkRegressedWhenDeltaIsWithinThreshold() {
        given(metricStore.statsByMethod(baseCommit)).willReturn(Map.of(SIG, stats(100.0)));
        given(metricStore.statsByMethod(headCommit)).willReturn(Map.of(SIG, stats(119.0)));

        List<MethodDiff> diffs = diffService.diff(baseCommit, headCommit).diffs();

        assertThat(diffs.get(0).deltaNs()).isEqualTo(19.0);
        assertThat(diffs.get(0).regressed()).isFalse();
    }

    @Test
    void shouldNotMarkRegressedWhenDeltaIsWithinExactlyThreshold() {
        given(metricStore.statsByMethod(baseCommit)).willReturn(Map.of(SIG, stats(100.0)));
        given(metricStore.statsByMethod(headCommit)).willReturn(Map.of(SIG, stats(120.0)));

        List<MethodDiff> diffs = diffService.diff(baseCommit, headCommit).diffs();

        assertThat(diffs.get(0).deltaNs()).isEqualTo(20.0);
        assertThat(diffs.get(0).regressed()).isFalse();
    }

    @Test
    void shouldMarkRegressedWhenBaseElapsedNsIsZeroAndHeadIsPositive() {
        given(metricStore.statsByMethod(baseCommit)).willReturn(Map.of(SIG, stats(0.0)));
        given(metricStore.statsByMethod(headCommit)).willReturn(Map.of(SIG, stats(100.0)));

        List<MethodDiff> diffs = diffService.diff(baseCommit, headCommit).diffs();

        assertThat(diffs.get(0).deltaNs()).isEqualTo(100.0);
        assertThat(diffs.get(0).regressed()).isTrue();
    }
}

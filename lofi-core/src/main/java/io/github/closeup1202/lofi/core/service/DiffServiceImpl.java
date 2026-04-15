package io.github.closeup1202.lofi.core.service;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.domain.MethodDiff;
import io.github.closeup1202.lofi.core.domain.MethodStats;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link DiffService}.
 *
 * <p>Computes per-method latency diffs by comparing the average elapsed time in
 * the base and head deploy snapshots. A method is flagged as regressed when
 * {@code (headNs - baseNs) / baseNs > regressionThreshold}.
 *
 * <p>Methods present only in the head deploy (new methods) are included but never
 * flagged as regressions. Methods present only in the base deploy (removed methods)
 * are included with a head latency of 0 ns and {@code regressed = false}.
 */
public class DiffServiceImpl implements DiffService {

    private final ReadableMetricStore metricStore;
    private final double regressionThreshold;

    /**
     * @param metricStore         store used to load deploy snapshots for comparison
     * @param regressionThreshold relative latency increase (0–1) above which a method is flagged as regressed
     */
    public DiffServiceImpl(ReadableMetricStore metricStore, double regressionThreshold) {
        this.metricStore = metricStore;
        this.regressionThreshold = regressionThreshold;
    }

    private static final MethodStats ABSENT = new MethodStats(0.0, 0.0, 0.0, 0);

    @Override
    public DiffResult diff(String baseCommit, String headCommit) {
        DeploySnapshot base = metricStore.snapshot(baseCommit);
        DeploySnapshot head = metricStore.snapshot(headCommit);

        Map<String, MethodStats> baseStats = base.statsByMethod();
        Map<String, MethodStats> headStats = head.statsByMethod();

        List<MethodDiff> diffs = new ArrayList<>();

        headStats.forEach((signature, hs) -> {
            boolean existsInBase = baseStats.containsKey(signature);
            MethodStats bs = existsInBase ? baseStats.get(signature) : ABSENT;
            double deltaNs = hs.avgNs() - bs.avgNs();
            boolean regressed;
            if (!existsInBase) {
                regressed = false;
            } else if (bs.avgNs() == 0.0) {
                regressed = hs.avgNs() > 0;
            } else {
                regressed = (deltaNs / bs.avgNs()) > regressionThreshold;
            }
            diffs.add(new MethodDiff(
                    signature,
                    bs.avgNs(), hs.avgNs(), deltaNs, regressed,
                    bs.p95Ns(), hs.p95Ns(),
                    bs.p99Ns(), hs.p99Ns(),
                    bs.count(), hs.count()
            ));
        });

        baseStats.forEach((signature, bs) -> {
            if (!headStats.containsKey(signature)) {
                diffs.add(new MethodDiff(
                        signature,
                        bs.avgNs(), 0.0, -bs.avgNs(), false,
                        bs.p95Ns(), 0.0,
                        bs.p99Ns(), 0.0,
                        bs.count(), 0
                ));
            }
        });

        return new DiffResult(baseCommit, headCommit, diffs);
    }
}

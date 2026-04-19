package io.github.closeup1202.lofi.core.service;

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
 * <p>Computes per-method latency diffs by comparing aggregated stats between the base and
 * head deploys. Stats are fetched via {@link ReadableMetricStore#statsByMethod}, which
 * SQLite-backed stores resolve with a DB-level window-function query — avoiding loading
 * all raw rows into the JVM heap.
 *
 * <p>A method is flagged as regressed when {@code (headNs - baseNs) / baseNs > regressionThreshold}.
 * Methods present only in the head deploy are never flagged as regressions.
 * Methods present only in the base deploy are included with head latency 0 ns and
 * {@code regressed = false}.
 */
public class DiffServiceImpl implements DiffService {

    private final ReadableMetricStore metricStore;
    private final double regressionThreshold;

    public DiffServiceImpl(ReadableMetricStore metricStore, double regressionThreshold) {
        this.metricStore = metricStore;
        this.regressionThreshold = regressionThreshold;
    }

    private static final MethodStats ABSENT = new MethodStats(0.0, 0.0, 0.0, 0);

    @Override
    public DiffResult diff(String baseCommit, String headCommit) {
        Map<String, MethodStats> baseStats = metricStore.statsByMethod(baseCommit);
        Map<String, MethodStats> headStats = metricStore.statsByMethod(headCommit);

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

        return new DiffResult(baseCommit, headCommit, diffs, regressionThreshold);
    }
}

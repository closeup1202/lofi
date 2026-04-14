package io.github.closeup1202.lofi.core.service;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.domain.MethodDiff;
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

    @Override
    public DiffResult diff(String baseCommit, String headCommit) {
        DeploySnapshot base = metricStore.snapshot(baseCommit);
        DeploySnapshot head = metricStore.snapshot(headCommit);

        Map<String, Double> baseAvg = base.averageByMethod();
        Map<String, Double> headAvg = head.averageByMethod();

        List<MethodDiff> diffs = new ArrayList<>();

        headAvg.forEach((signature, headNs) -> {
            boolean existsInBase = baseAvg.containsKey(signature);
            double baseNs = existsInBase ? baseAvg.get(signature) : 0.0;
            double deltaNs = headNs - baseNs;
            boolean regressed;
            if (!existsInBase) {
                regressed = false;
            } else if (baseNs == 0.0) {
                regressed = headNs > 0;
            } else {
                regressed = (deltaNs / baseNs) > regressionThreshold;
            }
            diffs.add(new MethodDiff(signature, baseNs, headNs, deltaNs, regressed));
        });

        baseAvg.forEach((signature, baseNs) -> {
            if (!headAvg.containsKey(signature)) {
                diffs.add(new MethodDiff(signature, baseNs, 0.0, -baseNs, false));
            }
        });

        return new DiffResult(baseCommit, headCommit, diffs);
    }
}

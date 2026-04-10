package io.github.closeup1202.lofi.actuator.service;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.domain.MethodDiff;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.MetricStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiffServiceImpl implements DiffService {

    private final MetricStore metricStore;
    private final double regressionThreshold;

    public DiffServiceImpl(MetricStore metricStore, double regressionThreshold) {
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

        headAvg.forEach((signature, headMs) -> {
            boolean existsInBase = baseAvg.containsKey(signature);
            double baseMs = existsInBase ? baseAvg.get(signature) : 0.0;
            double deltaMs = headMs - baseMs;
            boolean regressed;
            if (!existsInBase) {
                regressed = false; // new method, no baseline to compare
            } else if (baseMs == 0.0) {
                regressed = headMs > 0; // was 0ms, now has latency
            } else {
                regressed = (deltaMs / baseMs) > regressionThreshold;
            }
            diffs.add(new MethodDiff(signature, baseMs, headMs, deltaMs, regressed));
        });

        // A method only in base (removed from head)
        baseAvg.forEach((signature, baseMs) -> {
            if (!headAvg.containsKey(signature)) {
                diffs.add(new MethodDiff(signature, baseMs, 0.0, -baseMs, false));
            }
        });

        return new DiffResult(baseCommit, headCommit, diffs);
    }
}

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

    private static final double REGRESSION_THRESHOLD = 0.2;

    private final MetricStore metricStore;

    public DiffServiceImpl(MetricStore metricStore) {
        this.metricStore = metricStore;
    }

    @Override
    public DiffResult diff(String baseCommit, String headCommit) {
        DeploySnapshot base = metricStore.snapshot(baseCommit);
        DeploySnapshot head = metricStore.snapshot(headCommit);

        Map<String, Double> baseAvg = base.averageByMethod();
        Map<String, Double> headAvg = head.averageByMethod();

        List<MethodDiff> diffs = new ArrayList<>();

        headAvg.forEach((signature, headMs) -> {
            double baseMs = baseAvg.getOrDefault(signature, 0.0);
            double deltaMs = headMs - baseMs;
            boolean regressed = baseMs > 0 && (deltaMs / baseMs) > REGRESSION_THRESHOLD;
            diffs.add(new MethodDiff(signature, baseMs, headMs, deltaMs, regressed));
        });

        return new DiffResult(baseCommit, headCommit, diffs);
    }
}

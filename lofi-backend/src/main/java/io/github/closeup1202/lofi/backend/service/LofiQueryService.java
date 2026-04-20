package io.github.closeup1202.lofi.backend.service;

import io.github.closeup1202.lofi.backend.api.exception.CommitNotFoundException;
import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.domain.MethodStats;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import io.github.closeup1202.lofi.core.view.DeploySnapshotView;

import java.util.List;
import java.util.Map;

public class LofiQueryService {

    private final ReadableMetricStore metricStore;
    private final DiffService diffService;

    public LofiQueryService(ReadableMetricStore metricStore, DiffService diffService) {
        this.metricStore = metricStore;
        this.diffService = diffService;
    }

    public List<CommitSummary> listCommits() {
        return metricStore.listCommits();
    }

    public DeploySnapshotView snapshot(String commitHash) {
        Map<String, MethodStats> stats = metricStore.statsByMethod(commitHash);
        if (stats.isEmpty()) {
            throw new CommitNotFoundException(commitHash);
        }
        return DeploySnapshotView.from(commitHash, metricStore.deployedAt(commitHash), stats);
    }

    public DiffResult diff(String base, String head) {
        return diffService.diff(base, head);
    }
}

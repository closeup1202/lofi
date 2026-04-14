package io.github.closeup1202.lofi.backend.service;

import io.github.closeup1202.lofi.backend.api.exception.CommitNotFoundException;
import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;

import java.util.List;

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

    public DeploySnapshot snapshot(String commitHash) {
        DeploySnapshot snapshot = metricStore.snapshot(commitHash);
        if (snapshot.metrics().isEmpty()) {
            throw new CommitNotFoundException(commitHash);
        }
        return snapshot;
    }

    public DiffResult diff(String base, String head) {
        return diffService.diff(base, head);
    }
}

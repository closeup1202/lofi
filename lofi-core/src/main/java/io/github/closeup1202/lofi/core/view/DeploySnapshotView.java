package io.github.closeup1202.lofi.core.view;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;

import java.time.Instant;
import java.util.List;

public record DeploySnapshotView(
        String commitHash,
        Instant deployedAt,
        List<MethodMetricView> metrics
) {
    public static DeploySnapshotView from(DeploySnapshot snapshot) {
        return new DeploySnapshotView(
                snapshot.commitHash(),
                snapshot.deployedAt(),
                snapshot.metrics().stream().map(MethodMetricView::from).toList()
        );
    }
}

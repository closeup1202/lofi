package io.github.closeup1202.lofi.actuator.endpoint.view;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;

import java.time.Instant;
import java.util.List;

/**
 * HTTP response view of {@link DeploySnapshot}.
 * All metric elapsed times are expressed in milliseconds.
 *
 * @param commitHash commit hash identifying this deploy
 * @param deployedAt timestamp of the earliest recorded metric
 * @param metrics    per-invocation measurements in milliseconds
 */
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

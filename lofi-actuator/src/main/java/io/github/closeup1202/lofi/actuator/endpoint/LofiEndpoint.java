package io.github.closeup1202.lofi.actuator.endpoint;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

/**
 * Spring Boot Actuator endpoint that exposes deploy snapshots for a given commit hash.
 *
 * <p>Accessible at {@code GET /actuator/lofi/{commitHash}}.
 * Requires {@code lofi} to be included in {@code management.endpoints.web.exposure.include}.
 */
@Endpoint(id = "lofi")
public class LofiEndpoint {

    private final MetricStore metricStore;

    public LofiEndpoint(MetricStore metricStore) {
        this.metricStore = metricStore;
    }

    /**
     * Returns the deploy snapshot for the given commit hash.
     *
     * @param commitHash the commit hash identifying the deploy
     * @return snapshot containing all metrics recorded for that deploy
     */
    @ReadOperation
    public DeploySnapshot snapshot(@Selector String commitHash) {
        return metricStore.snapshot(commitHash);
    }
}

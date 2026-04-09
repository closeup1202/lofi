package io.github.closeup1202.lofi.actuator.endpoint;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.context.properties.bind.Name;

@Endpoint(id = "lofi")
public class LofiEndpoint {

    private final MetricStore metricStore;

    public LofiEndpoint(MetricStore metricStore) {
        this.metricStore = metricStore;
    }

    @ReadOperation
    public DeploySnapshot snapshot(@Selector @Name("commitHash") String commitHash) {
        return metricStore.snapshot(commitHash);
    }
}

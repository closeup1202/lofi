package io.github.closeup1202.lofi.actuator.endpoint;

import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.port.DiffService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

@Endpoint(id = "lofi-diff")
public class LofiDiffEndpoint {

    private final DiffService diffService;

    public LofiDiffEndpoint(DiffService diffService) {
        this.diffService = diffService;
    }

    @ReadOperation
    public DiffResult diff(String base, String head) {
        return diffService.diff(base, head);
    }
}

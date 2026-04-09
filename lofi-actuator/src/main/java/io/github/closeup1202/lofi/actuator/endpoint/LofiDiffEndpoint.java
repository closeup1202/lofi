package io.github.closeup1202.lofi.actuator.endpoint;

import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.port.DiffService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.context.properties.bind.Name;

@Endpoint(id = "lofi-diff")
public class LofiDiffEndpoint {

    private final DiffService diffService;

    public LofiDiffEndpoint(DiffService diffService) {
        this.diffService = diffService;
    }

    @ReadOperation
    public DiffResult diff(@Name("base") String base, @Name("head") String head) {
        return diffService.diff(base, head);
    }
}

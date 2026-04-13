package io.github.closeup1202.lofi.actuator.endpoint;

import io.github.closeup1202.lofi.core.domain.DiffResult;
import io.github.closeup1202.lofi.core.port.DiffService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Spring Boot Actuator endpoint that computes a method-level latency diff between two deploys.
 *
 * <p>Accessible at {@code GET /actuator/lofiDiff?base={baseCommit}&head={headCommit}}.
 * Requires {@code lofiDiff} to be included in {@code management.endpoints.web.exposure.include}.
 */
@Endpoint(id = "lofiDiff")
public class LofiDiffEndpoint {

    private final DiffService diffService;

    public LofiDiffEndpoint(DiffService diffService) {
        this.diffService = diffService;
    }

    /**
     * Computes and returns the method-level latency diff between two deploys.
     *
     * @param base commit hash of the reference (older) deploy
     * @param head commit hash of the target (newer) deploy
     * @return diff result with per-method latency comparisons and regression flags
     */
    @ReadOperation
    public DiffResult diff(String base, String head) {
        return diffService.diff(base, head);
    }
}

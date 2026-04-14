package io.github.closeup1202.lofi.actuator.endpoint;

import io.github.closeup1202.lofi.actuator.endpoint.view.DiffResultView;
import io.github.closeup1202.lofi.core.port.DiffService;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

/**
 * Spring Boot Actuator endpoint that computes a method-level latency diff between two deploys.
 *
 * <p>Accessible at {@code GET /actuator/lofiDiff?base={baseCommit}&head={headCommit}}.
 * Requires {@code lofiDiff} to be included in {@code management.endpoints.web.exposure.include}.
 */
@WebEndpoint(id = "lofiDiff")
public class LofiDiffEndpoint {

    private final DiffService diffService;

    /**
     * @param diffService service used to compute latency diffs between deploys
     */
    public LofiDiffEndpoint(DiffService diffService) {
        this.diffService = diffService;
    }

    /**
     * Computes and returns the method-level latency diff between two deploys.
     * All latency values are expressed in milliseconds for readability.
     *
     * @param base commit hash of the reference (older) deploy
     * @param head commit hash of the target (newer) deploy
     * @return diff result with per-method latency comparisons in ms and regression flags
     */
    @ReadOperation
    public DiffResultView diff(String base, String head) {
        return DiffResultView.from(diffService.diff(base, head));
    }
}

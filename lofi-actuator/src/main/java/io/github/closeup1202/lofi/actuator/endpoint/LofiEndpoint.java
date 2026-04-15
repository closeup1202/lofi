package io.github.closeup1202.lofi.actuator.endpoint;

import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.port.DiffService;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import io.github.closeup1202.lofi.core.view.DeploySnapshotView;
import io.github.closeup1202.lofi.core.view.DiffResultView;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Spring Boot Actuator endpoint exposing lofi metrics.
 *
 * <ul>
 *   <li>{@code GET /actuator/lofi}                          — list all recorded commits</li>
 *   <li>{@code GET /actuator/lofi/{commitHash}}             — snapshot for a commit</li>
 *   <li>{@code GET /actuator/lofi/diff?base=X&head=Y}       — latency diff between two commits</li>
 * </ul>
 * <p>
 * Requires {@code lofi} to be included in {@code management.endpoints.web.exposure.include}.
 */
@WebEndpoint(id = "lofi")
public class LofiEndpoint {

    private final ReadableMetricStore metricStore;
    private final DiffService diffService;

    public LofiEndpoint(ReadableMetricStore metricStore, DiffService diffService) {
        this.metricStore = metricStore;
        this.diffService = diffService;
    }

    /**
     * Returns a summary list of all recorded deploys, ordered by deploy time descending.
     */
    @ReadOperation
    public List<CommitSummary> commits() {
        return metricStore.listCommits();
    }

    /**
     * Routes sub-path requests:
     * <ul>
     *   <li>{@code /diff?base=X&head=Y} — computes latency diff between two commits</li>
     *   <li>{@code /{commitHash}}        — returns the deploy snapshot for that commit</li>
     * </ul>
     */
    @ReadOperation
    public Object read(
            @Selector(match = Selector.Match.ALL_REMAINING) String[] segments,
            @Nullable String base,
            @Nullable String head) {

        if (segments.length == 1 && "diff".equals(segments[0])) {
            if (!StringUtils.hasText(base) || !StringUtils.hasText(head)) {
                throw new IllegalArgumentException(
                        "Both 'base' and 'head' query params are required. " +
                                "Usage: /actuator/lofi/diff?base=<commitHash>&head=<commitHash>");
            }
            return DiffResultView.from(diffService.diff(base, head));
        }

        if (segments.length == 1) {
            return DeploySnapshotView.from(metricStore.snapshot(segments[0]));
        }

        throw new IllegalArgumentException(
                "Unknown path: /actuator/lofi/" + String.join("/", segments));
    }
}

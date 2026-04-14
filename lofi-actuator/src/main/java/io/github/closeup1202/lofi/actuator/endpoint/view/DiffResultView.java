package io.github.closeup1202.lofi.actuator.endpoint.view;

import io.github.closeup1202.lofi.core.domain.DiffResult;

import java.util.List;

/**
 * HTTP response view of {@link DiffResult}.
 * All latency values are expressed in milliseconds.
 *
 * @param baseCommit commit hash of the reference (older) deploy
 * @param headCommit commit hash of the target (newer) deploy
 * @param diffs      per-method latency comparisons in milliseconds
 */
public record DiffResultView(
        String baseCommit,
        String headCommit,
        List<MethodDiffView> diffs
) {
    public static DiffResultView from(DiffResult result) {
        return new DiffResultView(
                result.baseCommit(),
                result.headCommit(),
                result.diffs().stream().map(MethodDiffView::from).toList()
        );
    }
}

package io.github.closeup1202.lofi.core.domain;

import java.util.List;

/**
 * The result of comparing method-level latency between two deploys.
 * Contains a {@link MethodDiff} entry for every method that appeared in either deploy.
 *
 * @param baseCommit          commit hash of the reference (older) deploy
 * @param headCommit          commit hash of the target (newer) deploy
 * @param diffs               per-method latency comparison between the two deploys
 * @param regressionThreshold the relative threshold used to flag regressions (e.g. 0.2 = 20%)
 */
public record DiffResult(
        String baseCommit,
        String headCommit,
        List<MethodDiff> diffs,
        double regressionThreshold
) {
}

package io.github.closeup1202.lofi.core.view;

import io.github.closeup1202.lofi.core.domain.DiffResult;

import java.util.List;

public record DiffResultView(
        String baseCommit,
        String headCommit,
        List<MethodDiffView> diffs,
        double regressionThreshold
) {
    public static DiffResultView from(DiffResult result) {
        return new DiffResultView(
                result.baseCommit(),
                result.headCommit(),
                result.diffs().stream().map(MethodDiffView::from).toList(),
                result.regressionThreshold()
        );
    }
}

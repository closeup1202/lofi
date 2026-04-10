package io.github.closeup1202.lofi.core.domain;

import java.util.List;

public record DiffResult(
        String baseCommit,
        String headCommit,
        List<MethodDiff> diffs
) {
}

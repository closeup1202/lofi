package io.github.closeup1202.lofi.core.view;

import io.github.closeup1202.lofi.core.domain.MethodDiff;

public record MethodDiffView(
        String signature,
        double baseMs,
        double headMs,
        double deltaMs,
        boolean regressed
) {
    public static MethodDiffView from(MethodDiff diff) {
        return new MethodDiffView(
                diff.signature(),
                diff.baseNs() / 1_000_000.0,
                diff.headNs() / 1_000_000.0,
                diff.deltaNs() / 1_000_000.0,
                diff.regressed()
        );
    }
}

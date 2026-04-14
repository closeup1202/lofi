package io.github.closeup1202.lofi.actuator.endpoint.view;

import io.github.closeup1202.lofi.core.domain.MethodDiff;

/**
 * HTTP response view of {@link MethodDiff}.
 * Converts the internally stored nanosecond averages to milliseconds for readability.
 *
 * @param signature method signature
 * @param baseMs    average latency of the base deploy in milliseconds
 * @param headMs    average latency of the head deploy in milliseconds
 * @param deltaMs   latency difference in milliseconds (headMs - baseMs)
 * @param regressed {@code true} if the method exceeded the configured regression threshold
 */
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

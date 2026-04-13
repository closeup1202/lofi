package io.github.closeup1202.lofi.core.domain;

/**
 *
 * @param signature method signature
 * @param baseMs    average latency of the base (reference) deploy
 * @param headMs    average latency of the head (target) deploy
 * @param deltaMs   difference between deploys: headMs - baseMs. Positive means slower, negative means faster.
 * @param regressed flag indicating whether this method has degraded in performance (e.g. deltaMs / baseMs &gt; 0.2 for a 20%+ increase)
 */
public record MethodDiff(
        String signature,
        double baseMs,
        double headMs,
        double deltaMs,
        boolean regressed
) {
}
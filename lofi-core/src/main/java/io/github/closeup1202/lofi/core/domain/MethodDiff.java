package io.github.closeup1202.lofi.core.domain;

/**
 *
 * @param signature method signature
 * @param baseNs    average latency of the base (reference) deploy in nanoseconds
 * @param headNs    average latency of the head (target) deploy in nanoseconds
 * @param deltaNs   difference between deploys in nanoseconds: headNs - baseNs. Positive means slower, negative means faster.
 * @param regressed flag indicating whether this method has degraded in performance (e.g. deltaNs / baseNs &gt; 0.2 for a 20%+ increase)
 */
public record MethodDiff(
        String signature,
        double baseNs,
        double headNs,
        double deltaNs,
        boolean regressed
) {
}
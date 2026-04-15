package io.github.closeup1202.lofi.core.domain;

/**
 *
 * @param signature method signature
 * @param baseNs    average latency of the base (reference) deploy in nanoseconds
 * @param headNs    average latency of the head (target) deploy in nanoseconds
 * @param deltaNs   difference between deploys in nanoseconds: headNs - baseNs. Positive means slower, negative means faster.
 * @param regressed flag indicating whether this method has degraded in performance (e.g. deltaNs / baseNs &gt; 0.2 for a 20%+ increase)
 * @param baseP95Ns 95th-percentile latency of the base deploy in nanoseconds
 * @param headP95Ns 95th-percentile latency of the head deploy in nanoseconds
 * @param baseP99Ns 99th-percentile latency of the base deploy in nanoseconds
 * @param headP99Ns 99th-percentile latency of the head deploy in nanoseconds
 * @param baseCount number of invocations recorded in the base deploy
 * @param headCount number of invocations recorded in the head deploy
 */
public record MethodDiff(
        String signature,
        double baseNs,
        double headNs,
        double deltaNs,
        boolean regressed,
        double baseP95Ns,
        double headP95Ns,
        double baseP99Ns,
        double headP99Ns,
        int baseCount,
        int headCount
) {
}
package io.github.closeup1202.lofi.core.domain;

import java.time.Instant;

public record MethodMetric(
        String className,
        String methodName,
        long elapsedMs,
        Instant recordedAt
) {
    public String signature() {
        return className + "." + methodName + "()";
    }
}

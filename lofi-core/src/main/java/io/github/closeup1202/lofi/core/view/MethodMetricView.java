package io.github.closeup1202.lofi.core.view;

import io.github.closeup1202.lofi.core.domain.MethodMetric;

import java.time.Instant;

public record MethodMetricView(
        String className,
        String methodName,
        double elapsedMs,
        Instant recordedAt
) {
    public static MethodMetricView from(MethodMetric m) {
        return new MethodMetricView(
                m.className(),
                m.methodName(),
                m.elapsedNs() / 1_000_000.0,
                m.recordedAt()
        );
    }
}

package io.github.closeup1202.lofi.actuator.endpoint.view;

import io.github.closeup1202.lofi.core.domain.MethodMetric;

import java.time.Instant;

/**
 * HTTP response view of {@link MethodMetric}.
 * Converts the internally stored nanosecond value to milliseconds for readability.
 *
 * @param className  fully qualified class name of the instrumented bean
 * @param methodName name of the intercepted method
 * @param elapsedMs  wall-clock execution time in milliseconds (converted from nanoseconds)
 * @param recordedAt timestamp when the measurement was captured
 */
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

package io.github.closeup1202.lofi.core.domain;

import java.time.Instant;

/**
 * A single method invocation measurement captured by lofi's AOP interceptor.
 *
 * @param className  fully qualified class name of the instrumented bean
 * @param methodName name of the intercepted method
 * @param elapsedMs  wall-clock execution time in milliseconds
 * @param recordedAt timestamp when the measurement was captured
 */
public record MethodMetric(
        String className,
        String methodName,
        long elapsedMs,
        Instant recordedAt
) {
    /**
     * Returns the method signature in {@code ClassName.methodName()} format,
     * used as a stable key when grouping and comparing metrics across deploys.
     *
     * @return dot-notation method signature
     */
    public String signature() {
        return className + "." + methodName + "()";
    }
}

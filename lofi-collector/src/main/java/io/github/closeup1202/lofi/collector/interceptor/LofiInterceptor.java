package io.github.closeup1202.lofi.collector.interceptor;

import io.github.closeup1202.lofi.collector.persistence.MetricBuffer;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Automatically instruments all beans annotated with {@code @Service}, {@code @Component},
 * {@code @Repository}, {@code @Controller}, or {@code @RestController}, creating a
 * {@link MethodMetric} and storing it via {@link MetricBuffer}.
 * Calls that throw exceptions are not recorded to prevent latency pollution.
 *
 * <p>The following types are excluded from instrumentation to prevent proxy conflicts:
 * <ul>
 *   <li>Spring framework internals ({@code org.springframework.*})</li>
 *   <li>Jakarta Servlet filters ({@code jakarta.servlet.Filter} and subclasses)</li>
 *   <li>Spring MVC handler interceptors ({@code HandlerInterceptor} and subclasses)</li>
 *   <li>AspectJ aspects ({@code @Aspect} annotated classes)</li>
 * </ul>
 */
@Aspect
public class LofiInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LofiInterceptor.class);

    private final MetricBuffer metricBuffer;

    /**
     * @param metricBuffer the buffer used to accumulate and flush metrics
     */
    public LofiInterceptor(MetricBuffer metricBuffer) {
        this.metricBuffer = metricBuffer;
    }

    /**
     * Around advice that measures the wall-clock execution time of the intercepted method
     * and records a {@link MethodMetric} on success. Exceptions are propagated unchanged
     * and no metric is recorded.
     *
     * @param pjp the proceeding join point
     * @return the return value of the intercepted method
     * @throws Throwable if the intercepted method throws
     */

    @Around("(within(@org.springframework.stereotype.Service *)" +
            " || within(@org.springframework.stereotype.Component *)" +
            " || within(@org.springframework.stereotype.Repository *)" +
            " || within(@org.springframework.stereotype.Controller *)" +
            " || within(@org.springframework.web.bind.annotation.RestController *))" +
            " && !within(io.github.closeup1202.lofi.collector..*)" +
            " && !within(org.springframework..*)" +
            " && !within(jakarta.servlet.Filter+)" +
            " && !within(@org.aspectj.lang.annotation.Aspect *)" +
            " && !within(org.springframework.web.servlet.HandlerInterceptor+)")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = pjp.proceed();
        long elapsed = System.currentTimeMillis() - start;
        String className = pjp.getTarget().getClass().getName();
        String methodName = pjp.getSignature().getName();
        try {
            metricBuffer.add(new MethodMetric(className, methodName, elapsed, Instant.now()));
        } catch (Exception e) {
            log.warn("[lofi] Failed to record metric for {}.{}(): {}", className, methodName, e.getMessage());
        }
        return result;
    }
}

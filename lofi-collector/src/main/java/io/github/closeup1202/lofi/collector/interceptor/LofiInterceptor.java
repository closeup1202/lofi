package io.github.closeup1202.lofi.collector.interceptor;

import io.github.closeup1202.lofi.collector.persistence.MetricBuffer;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;

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
 *   <li>JDK dynamic proxies ({@code jdk.proxy*} / {@code $Proxy*}) — Spring Data JPA repositories
 *       are JDK proxies, and in nested-proxy scenarios {@code pjp.getTarget()} returns the proxy
 *       itself rather than the concrete {@code SimpleJpaRepository}. These entries produce
 *       meaningless {@code $Proxy173}-style class names and are already captured through the
 *       enclosing service-layer measurement.</li>
 *   <li>User-provided package patterns via {@code lofi.exclude-packages} — supports
 *       {@code com.foo.*} (package-boundary match) and {@code com.foo} (legacy
 *       {@code startsWith}). Useful for self-monitoring/ops endpoints whose recorded
 *       latencies would inflate on every dashboard refresh and drown out real
 *       application signal.</li>
 * </ul>
 */
@Aspect
public class LofiInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LofiInterceptor.class);

    private final MetricBuffer metricBuffer;
    private final List<String> excludePackages;

    /**
     * @param metricBuffer    the buffer used to accumulate and flush metrics
     * @param excludePackages package patterns whose beans should not be recorded. Supports
     *                        {@code com.foo.*} (matches {@code com.foo} and any sub-package,
     *                        respecting package boundaries) and {@code com.foo} (legacy
     *                        prefix match via {@code startsWith}). {@code null} and an empty
     *                        list both disable exclusion.
     */
    public LofiInterceptor(MetricBuffer metricBuffer, List<String> excludePackages) {
        this.metricBuffer = metricBuffer;
        this.excludePackages = excludePackages == null ? List.of() : List.copyOf(excludePackages);
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
        // Skip JDK dynamic proxies (e.g. Spring Data JPA repositories).
        // In nested-proxy chains pjp.getTarget() returns the proxy object itself,
        // producing meaningless "$Proxy173"-style names. Their execution time is
        // already captured by the enclosing service-layer measurement.
        if (Proxy.isProxyClass(pjp.getTarget().getClass())) {
            return pjp.proceed();
        }

        // User-configured exclusion. Done here (runtime) rather than in the pointcut,
        // because AspectJ pointcut strings are resolved at weaving time and cannot be
        // parameterised from ConfigurationProperties.
        String className = pjp.getTarget().getClass().getName();
        if (isExcluded(className)) {
            return pjp.proceed();
        }

        long start = System.nanoTime();
        Object result = pjp.proceed();
        long elapsedNs = System.nanoTime() - start;
        String methodName = pjp.getSignature().getName();
        try {
            metricBuffer.add(new MethodMetric(className, methodName, elapsedNs, Instant.now()));
            log.debug("[lofi] Recorded {}.{}() — {}ns", className, methodName, elapsedNs);
        } catch (Exception e) {
            log.warn("[lofi] Failed to record metric for {}.{}(): {}", className, methodName, e.getMessage());
        }
        return result;
    }

    /**
     * 매칭 규칙:
     * <ul>
     *   <li>{@code com.foo.*} — {@code com.foo} 자체 및 그 하위 전체 매치 (패키지 경계까지 고려).</li>
     *   <li>{@code com.foo}   — {@code startsWith} 매치 (레거시; {@code com.foobar} 도 매치될 수 있음).</li>
     * </ul>
     */
    private boolean isExcluded(String className) {
        if (excludePackages.isEmpty()) return false;
        for (String raw : excludePackages) {
            if (raw == null || raw.isBlank()) continue;
            String pattern = raw.trim();
            if (pattern.endsWith(".*")) {
                String base = pattern.substring(0, pattern.length() - 2);
                if (base.isEmpty()) continue;
                if (className.equals(base) || className.startsWith(base + ".")) return true;
            } else if (pattern.equals("*")) {
                return true;
            } else if (className.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }
}

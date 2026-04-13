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
 * Automatically instruments all beans annotated with @Service, @Component, @Repository,
 * @Controller, or @RestController, creating a MethodMetric and storing it via MetricStore.
 * Calls that throw exceptions are not recorded to prevent latency pollution.
 */
@Aspect
public class LofiInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LofiInterceptor.class);

    private final MetricBuffer metricBuffer;

    public LofiInterceptor(MetricBuffer metricBuffer) {
        this.metricBuffer = metricBuffer;
    }

    @Around("(within(@org.springframework.stereotype.Service *)" +
            " || within(@org.springframework.stereotype.Component *)" +
            " || within(@org.springframework.stereotype.Repository *)" +
            " || within(@org.springframework.stereotype.Controller *)" +
            " || within(@org.springframework.web.bind.annotation.RestController *))" +
            " && !within(io.github.closeup1202.lofi.collector..*)")
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

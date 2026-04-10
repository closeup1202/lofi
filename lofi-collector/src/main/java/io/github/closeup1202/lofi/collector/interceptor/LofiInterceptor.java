package io.github.closeup1202.lofi.collector.interceptor;

import io.github.closeup1202.lofi.collector.persistence.MetricBuffer;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.time.Instant;

/**
 * @Service @Component @Repository 붙은 빈 전체를 자동으로 계측하여
 * MethodMetric을 만들어서 MetricStore로 저장
 */
@Aspect
public class LofiInterceptor {

    private final MetricBuffer metricBuffer;

    public LofiInterceptor(MetricBuffer metricBuffer) {
        this.metricBuffer = metricBuffer;
    }

    @Around("(within(@org.springframework.stereotype.Service *)" +
            " || within(@org.springframework.stereotype.Component *)" +
            " || within(@org.springframework.stereotype.Repository *))" +
            " && !within(io.github.closeup1202.lofi.collector..*)")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String className = pjp.getTarget().getClass().getSimpleName();
            String methodName = pjp.getSignature().getName();
            metricBuffer.add(new MethodMetric(className, methodName, elapsed, Instant.now()));
        }
    }
}

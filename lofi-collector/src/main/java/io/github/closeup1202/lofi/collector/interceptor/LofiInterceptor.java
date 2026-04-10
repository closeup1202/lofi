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
 * @Service @Component @Repository @Controller @RestController 붙은 빈 전체를 자동으로 계측하여
 * MethodMetric을 만들어서 MetricStore로 저장.
 * 예외가 발생한 호출은 레이턴시 오염을 막기 위해 기록하지 않음.
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

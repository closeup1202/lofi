package io.github.closeup1202.lofi.collector.interceptor;

import io.github.closeup1202.lofi.collector.persistence.MetricBuffer;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LofiInterceptorTest {

    @Mock private MetricBuffer metricBuffer;
    @Mock private ProceedingJoinPoint pjp;
    @Mock private Signature signature;

    private LofiInterceptor interceptor;

    static class SampleTarget { }

    @BeforeEach
    void setUp() {
        interceptor = new LofiInterceptor(metricBuffer, List.of());
        lenient().when(pjp.getTarget()).thenReturn(new SampleTarget());
        lenient().when(pjp.getSignature()).thenReturn(signature);
        lenient().when(signature.getName()).thenReturn("testMethod");
    }

    @Test
    void shouldRecordMetricOnSuccessfulInvocation() throws Throwable {
        given(pjp.proceed()).willReturn("ok");

        interceptor.measure(pjp);

        verify(metricBuffer, times(1)).add(any(MethodMetric.class));
    }

    @Test
    void shouldNotRecordMetricWhenMethodThrowsException() throws Throwable {
        given(pjp.proceed()).willThrow(new RuntimeException("expected"));

        assertThatThrownBy(() -> interceptor.measure(pjp))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("expected");

        verify(metricBuffer, never()).add(any(MethodMetric.class));
    }

    @Test
    void shouldRecordFqcnAsClassName() throws Throwable {
        given(pjp.proceed()).willReturn("ok");

        interceptor.measure(pjp);

        verify(metricBuffer).add(argThat(metric ->
                metric.className().equals(SampleTarget.class.getName())
        ));
    }

    @Test
    void shouldRecordCorrectMethodName() throws Throwable {
        given(pjp.proceed()).willReturn("ok");

        interceptor.measure(pjp);

        verify(metricBuffer).add(argThat(metric ->
                metric.methodName().equals("testMethod")
        ));
    }

    @Test
    void shouldNotRecordMetricWhenClassMatchesExcludedPackage() throws Throwable {
        // SampleTarget 의 FQCN prefix 로 제외 지정 → 기록되면 안 된다.
        LofiInterceptor excluding = new LofiInterceptor(
                metricBuffer,
                List.of(SampleTarget.class.getPackageName())
        );
        given(pjp.proceed()).willReturn("ok");

        excluding.measure(pjp);

        verify(metricBuffer, never()).add(any(MethodMetric.class));
    }

    @Test
    void shouldRecordMetricWhenClassDoesNotMatchExcludedPackage() throws Throwable {
        LofiInterceptor excluding = new LofiInterceptor(
                metricBuffer,
                List.of("com.unrelated.other")
        );
        given(pjp.proceed()).willReturn("ok");

        excluding.measure(pjp);

        verify(metricBuffer, times(1)).add(any(MethodMetric.class));
    }

    @Test
    void shouldNotRecordMetricWhenClassMatchesWildcardPackage() throws Throwable {
        // "pkg.*" 는 pkg 와 그 하위 전체를 제외한다.
        LofiInterceptor excluding = new LofiInterceptor(
                metricBuffer,
                List.of(SampleTarget.class.getPackageName() + ".*")
        );
        given(pjp.proceed()).willReturn("ok");

        excluding.measure(pjp);

        verify(metricBuffer, never()).add(any(MethodMetric.class));
    }

    @Test
    void shouldRecordMetricWhenWildcardBaseIsNotPackageBoundary() throws Throwable {
        // "io.github.closeup1202.lofi.collector.intercept.*" 는
        // "io.github.closeup1202.lofi.collector.interceptor.*" 와 경계가 달라 매치되면 안 된다.
        String pkg = SampleTarget.class.getPackageName();
        String almost = pkg.substring(0, pkg.length() - 2) + ".*";
        LofiInterceptor excluding = new LofiInterceptor(
                metricBuffer,
                List.of(almost)
        );
        given(pjp.proceed()).willReturn("ok");

        excluding.measure(pjp);

        verify(metricBuffer, times(1)).add(any(MethodMetric.class));
    }

    @Test
    void shouldNotRecordMetricForJdkDynamicProxy() throws Throwable {
        // Spring Data JPA repositories are JDK dynamic proxies. In nested-proxy scenarios
        // pjp.getTarget() returns the proxy object itself, so we skip measurement.
        interface SampleRepository {}
        Object jdkProxy = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{SampleRepository.class},
                (proxy, method, args) -> null
        );
        given(pjp.getTarget()).willReturn(jdkProxy);
        given(pjp.proceed()).willReturn("ok");

        interceptor.measure(pjp);

        verify(metricBuffer, never()).add(any(MethodMetric.class));
    }
}

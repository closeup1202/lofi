package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricBufferTest {

    @Mock
    private MetricStore metricStore;

    private MethodMetric metric() {
        return new MethodMetric("TestClass", "testMethod", 10L, Instant.now());
    }

    @Test
    void shouldNotFlushBeforeThreshold() {
        MetricBuffer buffer = new MetricBuffer(metricStore, 5, 5000, 100);

        buffer.add(metric());
        buffer.add(metric());

        verify(metricStore, never()).saveAll(any());
    }

    @Test
    void shouldFlushWhenThresholdReached() {
        MetricBuffer buffer = new MetricBuffer(metricStore, 3, 5000, 100);

        buffer.add(metric());
        buffer.add(metric());
        buffer.add(metric()); // threshold 도달 → flush

        verify(metricStore, times(1)).saveAll(argThat(list -> list.size() == 3));
    }

    @Test
    void shouldFlushAllMetricsOnExplicitFlush() {
        MetricBuffer buffer = new MetricBuffer(metricStore, 100, 5000, 100);

        buffer.add(metric());
        buffer.add(metric());
        buffer.flush();

        verify(metricStore, times(1)).saveAll(argThat(list -> list.size() == 2));
    }

    @Test
    void shouldTriggerFlushWhenQueueIsFull() {
        // capacity=1, threshold=100 → overflow 시 flush 발동
        MetricBuffer buffer = new MetricBuffer(metricStore, 100, 5000, 1);

        buffer.add(metric()); // 큐 꽉 참
        buffer.add(metric()); // overflow → flush 호출

        verify(metricStore, atLeastOnce()).saveAll(any());
    }

    @Test
    void shouldNotFlushOnEmptyQueue() {
        MetricBuffer buffer = new MetricBuffer(metricStore, 3, 5000, 100);

        buffer.flush();

        verify(metricStore, never()).saveAll(any());
    }
}

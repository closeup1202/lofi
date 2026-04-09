package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class MetricBuffer implements SchedulingConfigurer {

    private final ArrayBlockingQueue<MethodMetric> queue = new ArrayBlockingQueue<>(1000);
    private final MetricStore metricStore;
    private final int flushThreshold;
    private final long flushDelayMs;

    public MetricBuffer(MetricStore metricStore, int flushThreshold, long flushDelayMs) {
        this.metricStore = metricStore;
        this.flushThreshold = flushThreshold;
        this.flushDelayMs = flushDelayMs;
    }

    public void add(MethodMetric metric) {
        boolean offered = queue.offer(metric); // 큐에 metric을 넣으려 시도. 성공하면 true, 큐가 꽉 찼으면(1000개) false 반환
        if (!offered) {
            flush();
            boolean retried = queue.offer(metric);
            if (!retried) {
                // TODO: 유실 카운트 로깅 — 나중에 메트릭화 가능
            }
        }
        if (queue.size() >= flushThreshold) {
            flush();
        }
    }

    public void flush() {
        List<MethodMetric> batch = new ArrayList<>();
        // 큐에 있는 모든 메트릭을 한 번에 batch로 옮김. 이 시점부터 큐는 비워지고, 다른 스레드가 새로 add해도 batch에는 안 들어옴
        // thread-safe하게 동작
        queue.drainTo(batch);
        if (batch.isEmpty()) return;
        batch.forEach(metricStore::save);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::flush, Duration.of(flushDelayMs, ChronoUnit.MILLIS));
    }
}

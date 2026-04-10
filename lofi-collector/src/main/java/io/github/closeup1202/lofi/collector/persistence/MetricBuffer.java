package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MetricBuffer implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(MetricBuffer.class);

    private final ArrayBlockingQueue<MethodMetric> queue;
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final MetricStore metricStore;
    private final int flushThreshold;
    private final long flushDelayMs;

    public MetricBuffer(MetricStore metricStore, int flushThreshold, long flushDelayMs, int queueCapacity) {
        this.metricStore = metricStore;
        this.flushThreshold = flushThreshold;
        this.flushDelayMs = flushDelayMs;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    public void add(MethodMetric metric) {
        boolean offered = queue.offer(metric);
        if (!offered) {
            flush();
            boolean retried = queue.offer(metric);
            if (!retried) {
                log.warn("[lofi] Metric dropped — queue still full after flush: {}", metric.signature());
            }
        }
        if (queue.size() >= flushThreshold && flushing.compareAndSet(false, true)) {
            try {
                flush();
            } finally {
                flushing.set(false);
            }
        }
    }

    public void flush() {
        List<MethodMetric> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (batch.isEmpty()) return;
        metricStore.saveAll(batch);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::flush, Duration.of(flushDelayMs, ChronoUnit.MILLIS));
    }
}

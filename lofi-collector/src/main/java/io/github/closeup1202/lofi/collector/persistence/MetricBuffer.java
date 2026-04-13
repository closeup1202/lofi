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

/**
 * An in-process buffer that accumulates {@link MethodMetric} instances and flushes them
 * to {@link MetricStore} in batches, reducing write pressure on the underlying storage.
 *
 * <p>Flushing is triggered in two ways:
 * <ul>
 *   <li><b>Threshold-based:</b> when the queue reaches {@code flushThreshold} items.</li>
 *   <li><b>Time-based:</b> on a fixed-delay schedule every {@code flushDelayMs} milliseconds,
 *       implemented via {@link SchedulingConfigurer}.</li>
 * </ul>
 *
 * <p>If the queue is full, an overflow flush is attempted before dropping the metric.
 */
public class MetricBuffer implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(MetricBuffer.class);

    private final ArrayBlockingQueue<MethodMetric> queue;
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final MetricStore metricStore;
    private final int flushThreshold;
    private final long flushDelayMs;

    /**
     * @param metricStore    store that receives flushed metric batches
     * @param flushThreshold queue size that triggers an immediate flush
     * @param flushDelayMs   interval in milliseconds between periodic scheduled flushes
     * @param queueCapacity  maximum number of metrics the buffer can hold before overflow
     */
    public MetricBuffer(MetricStore metricStore, int flushThreshold, long flushDelayMs, int queueCapacity) {
        this.metricStore = metricStore;
        this.flushThreshold = flushThreshold;
        this.flushDelayMs = flushDelayMs;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    /**
     * Adds a metric to the buffer. Triggers a flush if the queue reaches the threshold.
     * If the queue is full, flushes first and retries; logs a warning if it is still full.
     *
     * @param metric the metric to buffer
     */
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

    /**
     * Drains all buffered metrics and writes them to the store in a single batch.
     * No-ops if the buffer is empty.
     */
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

package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An in-process buffer that accumulates {@link MethodMetric} instances and flushes them
 * to {@link MetricStore} in batches, reducing write pressure on the underlying storage.
 *
 * <p>Flushing is triggered in two ways:
 * <ul>
 *   <li><b>Threshold-based:</b> when the queue reaches {@code flushThreshold} items.</li>
 *   <li><b>Time-based:</b> a single daemon thread runs a fixed-delay flush every
 *       {@code flushDelayMs} milliseconds, independently of Spring's scheduling.</li>
 * </ul>
 *
 * <p>If the queue is full, an overflow flush is attempted. If the queue remains full after
 * the flush, the oldest buffered metric is evicted to make room for the incoming one,
 * ensuring that recent measurements are always preserved over stale ones.
 *
 * <p>On application shutdown ({@link #destroy()}), the scheduler is stopped and a final
 * flush drains any remaining metrics so none are lost on graceful shutdown.
 */
public class MetricBuffer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MetricBuffer.class);

    private final ArrayBlockingQueue<MethodMetric> queue;
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final MetricStore metricStore;
    private final int flushThreshold;
    private final ScheduledExecutorService scheduler;

    /**
     * @param metricStore    store that receives flushed metric batches
     * @param flushThreshold queue size that triggers an immediate flush
     * @param flushDelayMs   interval in milliseconds between periodic scheduled flushes
     * @param queueCapacity  maximum number of metrics the buffer can hold before overflow
     */
    public MetricBuffer(MetricStore metricStore, int flushThreshold, long flushDelayMs, int queueCapacity) {
        this.metricStore = metricStore;
        this.flushThreshold = flushThreshold;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lofi-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::scheduledFlush, flushDelayMs, flushDelayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a metric to the buffer. Triggers a flush if the queue reaches the threshold.
     * If the queue is full, flushes first and retries; if still full, evicts the oldest
     * buffered metric to make room so that the most recent measurement is never lost.
     *
     * @param metric the metric to buffer
     */
    public void add(MethodMetric metric) {
        boolean offered = queue.offer(metric);
        if (!offered) {
            flush();
            boolean retried = queue.offer(metric);
            if (!retried) {
                // Queue is still full after flush — evict the oldest metric and insert the new one
                // so that recent measurements always take priority over stale ones.
                queue.poll();
                queue.offer(metric);
                log.warn("[lofi] Queue full after flush — oldest metric evicted to make room for: {}", metric.signature());
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

    private void scheduledFlush() {
        try {
            flush();
        } catch (Exception e) {
            log.warn("[lofi] Scheduled flush failed: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        flush();
    }
}

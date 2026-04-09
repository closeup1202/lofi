package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CopyOnWriteArrayList 쓴 건 AOP가 멀티스레드 환경에서 동시에 save()를 호출하기 때문
 * 락 없이 thread-safe하게 처리할 수 있음
 */
public class InMemoryMetricStore implements MetricStore {

    private final CopyOnWriteArrayList<MethodMetric> metrics = new CopyOnWriteArrayList<>();

    @Override
    public void save(MethodMetric metric) {
        metrics.add(metric);
    }

    @Override
    public DeploySnapshot snapshot(String commitHash) {
        return new DeploySnapshot(commitHash, Instant.now(), List.copyOf(metrics));
    }
}

package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * lofi.store-type=in-memory 설정 시 활성화됨
 * 단일 프로세스 내 테스트/개발 환경에서 SQLite 없이 동작 가능
 * 재시작 시 데이터 유실됨
 */
public class InMemoryMetricStore implements MetricStore {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MethodMetric>> metricsByCommit = new ConcurrentHashMap<>();
    private final DeployContext deployContext;

    public InMemoryMetricStore(DeployContext deployContext) {
        this.deployContext = deployContext;
    }

    @Override
    public void save(MethodMetric metric) {
        metricsByCommit
                .computeIfAbsent(deployContext.commitHash(), k -> new CopyOnWriteArrayList<>())
                .add(metric);
    }

    @Override
    public DeploySnapshot snapshot(String commitHash) {
        List<MethodMetric> metrics = List.copyOf(
                metricsByCommit.getOrDefault(commitHash, new CopyOnWriteArrayList<>())
        );
        Instant deployedAt = metrics.stream()
                .map(MethodMetric::recordedAt)
                .min(Instant::compareTo)
                .orElse(Instant.now());
        return new DeploySnapshot(commitHash, deployedAt, metrics);
    }
}

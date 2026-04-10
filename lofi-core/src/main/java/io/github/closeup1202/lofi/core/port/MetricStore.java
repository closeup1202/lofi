package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;

import java.util.List;

/**
 * TODO: multi-pod support
 * 멀티 Pod 환경에서는 중앙 스토리지(외부 DB 또는 대시보드)로 교체 필요
 * MetricStore 인터페이스만 갈아끼우면 됨
 */
public interface MetricStore {
    void save(MethodMetric metric);
    DeploySnapshot snapshot(String commitHash);

    default void saveAll(List<MethodMetric> metrics) {
        metrics.forEach(this::save);
    }
}

package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.MetricStore;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private final Deque<String> commitOrder = new ArrayDeque<>();
    private final DeployContext deployContext;
    private final int retentionCommits;

    public InMemoryMetricStore(DeployContext deployContext, int retentionCommits) {
        this.deployContext = deployContext;
        this.retentionCommits = retentionCommits;
    }

    @Override
    public void save(MethodMetric metric) {
        String commitHash = deployContext.commitHash();
        CopyOnWriteArrayList<MethodMetric> list = registerCommitIfAbsent(commitHash);
        list.add(metric);
    }

    /**
     * 새 커밋을 등록하고 retentionCommits 초과 시 가장 오래된 커밋을 제거한다.
     * commitOrder와 metricsByCommit의 정합성을 보장하기 위해 synchronized 처리.
     */
    private synchronized CopyOnWriteArrayList<MethodMetric> registerCommitIfAbsent(String commitHash) {
        if (!metricsByCommit.containsKey(commitHash)) {
            CopyOnWriteArrayList<MethodMetric> list = new CopyOnWriteArrayList<>();
            metricsByCommit.put(commitHash, list);
            commitOrder.addLast(commitHash);
            while (commitOrder.size() > retentionCommits) {
                String oldest = commitOrder.pollFirst();
                metricsByCommit.remove(oldest);
            }
        }
        return metricsByCommit.get(commitHash);
    }

    @Override
    public DeploySnapshot snapshot(String commitHash) {
        List<MethodMetric> metrics = List.copyOf(
                metricsByCommit.getOrDefault(commitHash, new CopyOnWriteArrayList<>())
        );
        Instant deployedAt = metrics.stream()
                .map(MethodMetric::recordedAt)
                .min(Instant::compareTo)
                .orElse(Instant.EPOCH);
        return new DeploySnapshot(commitHash, deployedAt, metrics);
    }
}

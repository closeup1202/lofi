package io.github.closeup1202.lofi.collector.persistence;

import io.github.closeup1202.lofi.collector.context.DeployContext;
import io.github.closeup1202.lofi.core.domain.CommitSummary;
import io.github.closeup1202.lofi.core.domain.DeploySnapshot;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.ReadableMetricStore;
import io.github.closeup1202.lofi.core.port.WritableMetricStore;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Activated when lofi.store-type=in-memory is set.
 * Operates without SQLite for test/development environments within a single process.
 * Data is lost on restart.
 */
public class InMemoryMetricStore implements ReadableMetricStore, WritableMetricStore {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MethodMetric>> metricsByCommit = new ConcurrentHashMap<>();
    private final Deque<String> commitOrder = new ArrayDeque<>();
    private final DeployContext deployContext;
    private final int retentionCommits;

    /**
     * @param deployContext    provides the current deploy's commit hash
     * @param retentionCommits maximum number of recent deploys to keep in memory
     */
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

    @Override
    public void saveAll(List<MethodMetric> metrics) {
        String commitHash = deployContext.commitHash();
        CopyOnWriteArrayList<MethodMetric> list = registerCommitIfAbsent(commitHash);
        list.addAll(metrics);
    }

    /**
     * Registers a new commit and evicts the oldest commit when retentionCommits is exceeded.
     * Synchronized to ensure consistency between commitOrder and metricsByCommit.
     */
    private synchronized CopyOnWriteArrayList<MethodMetric> registerCommitIfAbsent(String commitHash) {
        if (!metricsByCommit.containsKey(commitHash)) {
            CopyOnWriteArrayList<MethodMetric> list = new CopyOnWriteArrayList<>();
            metricsByCommit.put(commitHash, list);
            commitOrder.addLast(commitHash);
            while (commitOrder.size() > retentionCommits) {
                String oldest = commitOrder.pollFirst();
                if (StringUtils.hasLength(oldest)) {
                    metricsByCommit.remove(oldest);
                }
            }
        }
        return metricsByCommit.get(commitHash);
    }

    @Override
    public synchronized Instant deployedAt(String commitHash) {
        return metricsByCommit.getOrDefault(commitHash, new CopyOnWriteArrayList<>()).stream()
                .map(MethodMetric::recordedAt)
                .min(Instant::compareTo)
                .orElse(Instant.EPOCH);
    }

    @Override
    public synchronized List<CommitSummary> listCommits() {
        return commitOrder.stream()
                .map(hash -> {
                    List<MethodMetric> metrics = List.copyOf(
                            metricsByCommit.getOrDefault(hash, new CopyOnWriteArrayList<>())
                    );
                    Instant deployedAt = metrics.stream()
                            .map(MethodMetric::recordedAt)
                            .min(Instant::compareTo)
                            .orElse(Instant.EPOCH);
                    return new CommitSummary(hash, deployedAt, metrics.size());
                })
                .sorted((a, b) -> b.deployedAt().compareTo(a.deployedAt()))
                .toList();
    }

    @Override
    public synchronized DeploySnapshot snapshot(String commitHash) {
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

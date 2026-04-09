package io.github.closeup1202.lofi.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record DeploySnapshot(
        String commitHash,
        Instant deployedAt,
        List<MethodMetric> metrics
) {
    public Map<String, Double> averageByMethod() {
        return metrics.stream()
                .collect(Collectors.groupingBy(
                        MethodMetric::signature,
                        Collectors.averagingLong(MethodMetric::elapsedMs)
                ));
    }
}

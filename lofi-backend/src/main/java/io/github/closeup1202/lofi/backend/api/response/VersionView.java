package io.github.closeup1202.lofi.backend.api.response;

public record VersionView(
        String appName,
        String appVersion,
        int retentionCommits,
        double regressionThreshold
) {}

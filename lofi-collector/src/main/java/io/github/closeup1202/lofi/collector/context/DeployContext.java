package io.github.closeup1202.lofi.collector.context;

import org.springframework.util.StringUtils;

public record DeployContext(String commitHash) {
    public String commitHash() {
        if (!StringUtils.hasLength(commitHash)) {
            return "unknown";
        }
        return commitHash;
    }
}

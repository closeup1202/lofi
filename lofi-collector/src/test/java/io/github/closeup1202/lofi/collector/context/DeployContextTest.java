package io.github.closeup1202.lofi.collector.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeployContextTest {
    @Test
    void willReturnRevokedCommitHash() {
        DeployContext context = new DeployContext("a3f9c1");
        assertThat(context.commitHash()).isEqualTo("a3f9c1");
    }

    @Test
    void willReturnUnknownWithoutCommitHash() {
        DeployContext context = new DeployContext("unknown");
        assertThat(context.commitHash()).isEqualTo("unknown");
    }

    @Test
    void willReturnUnknownBlankedCommitHash() {
        DeployContext context = new DeployContext("");
        assertThat(context.commitHash()).isEqualTo("unknown");
    }
}
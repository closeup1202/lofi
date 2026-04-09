package io.github.closeup1202.lofi.collector.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeployContextTest {
    @Test
    void 커밋해시가_주입되면_반환한다() {
        DeployContext context = new DeployContext("a3f9c1");
        assertThat(context.commitHash()).isEqualTo("a3f9c1");
    }

    @Test
    void 커밋해시가_없으면_unknown을_반환한다() {
        DeployContext context = new DeployContext("unknown");
        assertThat(context.commitHash()).isEqualTo("unknown");
    }
}
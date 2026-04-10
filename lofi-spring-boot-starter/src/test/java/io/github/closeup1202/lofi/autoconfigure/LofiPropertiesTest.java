package io.github.closeup1202.lofi.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LofiPropertiesTest {

    private static LofiProperties.Buffer defaultBuffer() {
        return new LofiProperties.Buffer(100, 5000L, 1000);
    }

    @Test
    void shouldRejectInvalidStoreType() {
        assertThatThrownBy(() ->
                new LofiProperties("commit", "invalid", 0.2, 50, defaultBuffer())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("store-type");
    }

    @Test
    void shouldRejectNegativeRegressionThreshold() {
        assertThatThrownBy(() ->
                new LofiProperties("commit", "sqlite", -0.1, 50, defaultBuffer())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regression-threshold");
    }

    @Test
    void shouldRejectRegressionThresholdAboveOne() {
        assertThatThrownBy(() ->
                new LofiProperties("commit", "sqlite", 1.1, 50, defaultBuffer())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regression-threshold");
    }

    @Test
    void shouldRejectZeroRetentionCommits() {
        assertThatThrownBy(() ->
                new LofiProperties("commit", "sqlite", 0.2, 0, defaultBuffer())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retention-commits");
    }

    @Test
    void shouldRejectNegativeRetentionCommits() {
        assertThatThrownBy(() ->
                new LofiProperties("commit", "sqlite", 0.2, -1, defaultBuffer())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retention-commits");
    }

    @Test
    void shouldAcceptSqliteStoreType() {
        assertThatNoException().isThrownBy(() ->
                new LofiProperties("commit", "sqlite", 0.2, 50, defaultBuffer())
        );
    }

    @Test
    void shouldAcceptInMemoryStoreType() {
        assertThatNoException().isThrownBy(() ->
                new LofiProperties("commit", "in-memory", 0.2, 50, defaultBuffer())
        );
    }

    @Test
    void shouldAcceptBoundaryRegressionThresholds() {
        assertThatNoException().isThrownBy(() ->
                new LofiProperties("commit", "sqlite", 0.0, 50, defaultBuffer())
        );
        assertThatNoException().isThrownBy(() ->
                new LofiProperties("commit", "sqlite", 1.0, 50, defaultBuffer())
        );
    }
}

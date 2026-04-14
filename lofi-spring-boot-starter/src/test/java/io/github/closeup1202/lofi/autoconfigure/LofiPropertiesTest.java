package io.github.closeup1202.lofi.autoconfigure;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LofiPropertiesTest {

    private static final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static LofiProperties.Buffer defaultBuffer() {
        return new LofiProperties.Buffer(100, 5000L, 1000);
    }

    private static Set<ConstraintViolation<LofiProperties>> validate(LofiProperties props) {
        return validator.validate(props);
    }

    private static Set<ConstraintViolation<LofiProperties.Buffer>> validateBuffer(LofiProperties.Buffer buffer) {
        return validator.validate(buffer);
    }

    // ── storeType ─────────────────────────────────────────────────────────────

    @Test
    void shouldRejectInvalidStoreType() {
        Set<ConstraintViolation<LofiProperties>> violations =
                validate(new LofiProperties("commit", "invalid", 0.2, 50, defaultBuffer()));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("storeType"));
    }

    @Test
    void shouldAcceptSqliteStoreType() {
        assertThat(validate(new LofiProperties("commit", "sqlite", 0.2, 50, defaultBuffer()))).isEmpty();
    }

    @Test
    void shouldAcceptInMemoryStoreType() {
        assertThat(validate(new LofiProperties("commit", "in-memory", 0.2, 50, defaultBuffer()))).isEmpty();
    }

    // ── regressionThreshold ───────────────────────────────────────────────────

    @Test
    void shouldRejectNegativeRegressionThreshold() {
        Set<ConstraintViolation<LofiProperties>> violations =
                validate(new LofiProperties("commit", "sqlite", -0.1, 50, defaultBuffer()));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("regressionThreshold"));
    }

    @Test
    void shouldRejectRegressionThresholdAboveOne() {
        Set<ConstraintViolation<LofiProperties>> violations =
                validate(new LofiProperties("commit", "sqlite", 1.1, 50, defaultBuffer()));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("regressionThreshold"));
    }

    @Test
    void shouldAcceptBoundaryRegressionThresholds() {
        assertThat(validate(new LofiProperties("commit", "sqlite", 0.0, 50, defaultBuffer()))).isEmpty();
        assertThat(validate(new LofiProperties("commit", "sqlite", 1.0, 50, defaultBuffer()))).isEmpty();
    }

    // ── retentionCommits ──────────────────────────────────────────────────────

    @Test
    void shouldRejectZeroRetentionCommits() {
        Set<ConstraintViolation<LofiProperties>> violations =
                validate(new LofiProperties("commit", "sqlite", 0.2, 0, defaultBuffer()));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("retentionCommits"));
    }

    @Test
    void shouldRejectNegativeRetentionCommits() {
        Set<ConstraintViolation<LofiProperties>> violations =
                validate(new LofiProperties("commit", "sqlite", 0.2, -1, defaultBuffer()));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("retentionCommits"));
    }

    // ── multiple violations reported at once ─────────────────────────────────

    @Test
    void shouldReportAllViolationsAtOnce() {
        Set<ConstraintViolation<LofiProperties>> violations =
                validate(new LofiProperties("commit", "invalid", -0.1, 0, defaultBuffer()));

        assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("storeType"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("regressionThreshold"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("retentionCommits"));
    }

    // ── Buffer ────────────────────────────────────────────────────────────────

    @Test
    void shouldRejectZeroFlushThreshold() {
        Set<ConstraintViolation<LofiProperties.Buffer>> violations =
                validateBuffer(new LofiProperties.Buffer(0, 5000L, 1000));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("flushThreshold"));
    }

    @Test
    void shouldRejectZeroFlushDelayMs() {
        Set<ConstraintViolation<LofiProperties.Buffer>> violations =
                validateBuffer(new LofiProperties.Buffer(100, 0L, 1000));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("flushDelayMs"));
    }

    @Test
    void shouldRejectZeroQueueCapacity() {
        Set<ConstraintViolation<LofiProperties.Buffer>> violations =
                validateBuffer(new LofiProperties.Buffer(100, 5000L, 0));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("queueCapacity"));
    }
}

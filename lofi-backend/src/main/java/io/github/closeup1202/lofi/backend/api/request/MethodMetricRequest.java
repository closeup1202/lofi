package io.github.closeup1202.lofi.backend.api.request;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Wire-format DTO for a single metric in {@link IngestRequest}.
 * Validated at the API boundary so malformed entries are rejected with 400 before
 * touching the store; converts to the domain {@link MethodMetric} via {@link #toDomain()}.
 *
 * <p>{@code lofi-core} is kept dependency-free, so validation lives here rather than on
 * the domain record itself.
 */
public record MethodMetricRequest(
        @NotBlank
        @Size(max = 512)
        String className,

        @NotBlank
        @Size(max = 256)
        String methodName,

        @PositiveOrZero
        long elapsedNs,

        @NotNull
        Instant recordedAt
) {
    public MethodMetric toDomain() {
        return new MethodMetric(className, methodName, elapsedNs, recordedAt);
    }
}

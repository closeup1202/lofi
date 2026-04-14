package io.github.closeup1202.lofi.backend.api.request;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record IngestRequest(
        @NotBlank String commitHash,
        @NotEmpty List<MethodMetric> metrics
) {
}

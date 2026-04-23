package io.github.closeup1202.lofi.backend.api.request;

import io.github.closeup1202.lofi.core.domain.MethodMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record IngestRequest(
        @NotBlank
        @Pattern(regexp = "^[a-fA-F0-9]{7,40}$", message = "must be a hex git hash (7-40 chars)")
        String commitHash,

        @NotEmpty
        @Size(max = 10_000, message = "must not exceed 10000 metrics per request")
        List<MethodMetric> metrics
) {
}

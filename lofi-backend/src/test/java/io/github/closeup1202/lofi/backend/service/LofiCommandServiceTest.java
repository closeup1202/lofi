package io.github.closeup1202.lofi.backend.service;

import io.github.closeup1202.lofi.backend.api.request.IngestRequest;
import io.github.closeup1202.lofi.backend.api.request.MethodMetricRequest;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.IngestableStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LofiCommandServiceTest {

    @Mock
    private IngestableStore ingestableStore;

    private LofiCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new LofiCommandService(ingestableStore);
    }

    @Test
    void ingest_shouldDelegateToIngestableStore() {
        String commitHash = "a3f9c1";
        Instant now = Instant.now();
        List<MethodMetricRequest> metrics = List.of(
                new MethodMetricRequest("TestClass", "testMethod", 1_000_000L, now)
        );
        IngestRequest request = new IngestRequest(commitHash, metrics);

        commandService.ingest(request);

        @SuppressWarnings("unchecked")
        var captor = forClass((Class<List<MethodMetric>>) (Class<?>) List.class);
        verify(ingestableStore).ingest(org.mockito.ArgumentMatchers.eq(commitHash), captor.capture());
        assertThat(captor.getValue())
                .containsExactly(new MethodMetric("TestClass", "testMethod", 1_000_000L, now));
    }
}

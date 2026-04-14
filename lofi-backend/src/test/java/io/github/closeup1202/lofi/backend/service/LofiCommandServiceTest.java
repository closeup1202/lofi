package io.github.closeup1202.lofi.backend.service;

import io.github.closeup1202.lofi.backend.api.request.IngestRequest;
import io.github.closeup1202.lofi.core.domain.MethodMetric;
import io.github.closeup1202.lofi.core.port.WritableMetricStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LofiCommandServiceTest {

    @Mock
    private WritableMetricStore writableMetricStore;

    private LofiCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new LofiCommandService(writableMetricStore);
    }

    @Test
    void ingest_shouldDelegateToWritableMetricStore() {
        String commitHash = "a3f9c1";
        List<MethodMetric> metrics = List.of(
                new MethodMetric("TestClass", "testMethod", 1_000_000L, Instant.now())
        );
        IngestRequest request = new IngestRequest(commitHash, metrics);

        commandService.ingest(request);

        verify(writableMetricStore).ingest(commitHash, metrics);
    }
}

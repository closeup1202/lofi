package io.github.closeup1202.lofi.backend.service;

import io.github.closeup1202.lofi.backend.api.request.IngestRequest;
import io.github.closeup1202.lofi.core.port.IngestableStore;

public class LofiCommandService {

    private final IngestableStore ingestableStore;

    public LofiCommandService(IngestableStore ingestableStore) {
        this.ingestableStore = ingestableStore;
    }

    public void ingest(IngestRequest request) {
        ingestableStore.ingest(request.commitHash(), request.toDomainMetrics());
    }
}

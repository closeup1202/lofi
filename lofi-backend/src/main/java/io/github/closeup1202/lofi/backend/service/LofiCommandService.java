package io.github.closeup1202.lofi.backend.service;

import io.github.closeup1202.lofi.backend.api.request.IngestRequest;
import io.github.closeup1202.lofi.core.port.WritableMetricStore;

public class LofiCommandService {

    private final WritableMetricStore writableMetricStore;

    public LofiCommandService(WritableMetricStore writableMetricStore) {
        this.writableMetricStore = writableMetricStore;
    }

    public void ingest(IngestRequest request) {
        writableMetricStore.ingest(request.commitHash(), request.metrics());
    }
}

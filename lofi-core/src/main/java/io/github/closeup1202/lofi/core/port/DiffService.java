package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.DiffResult;

public interface DiffService {
    DiffResult diff(String baseCommit, String headCommit);
}
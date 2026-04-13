package io.github.closeup1202.lofi.core.port;

import io.github.closeup1202.lofi.core.domain.DiffResult;

/**
 * Port for computing method-level latency differences between two deploys.
 */
public interface DiffService {

    /**
     * Compares method latency between two deploys and returns per-method diff results,
     * including regression flags for methods that exceeded the configured threshold.
     *
     * @param baseCommit commit hash of the reference (older) deploy
     * @param headCommit commit hash of the target (newer) deploy
     * @return diff result containing per-method latency comparisons
     */
    DiffResult diff(String baseCommit, String headCommit);
}
package io.github.closeup1202.lofi.collector.context;

/**
 * Holds the commit hash of the currently running deploy, resolved from the
 * {@code GIT_COMMIT_HASH} environment variable at application startup.
 * Falls back to {@code "unknown"} if the value is absent or blank.
 */
public record DeployContext(String commitHash) {

    /**
     * Returns the commit hash for the current deploy.
     * Returns {@code "unknown"} if the hash was not configured.
     *
     * @return commit hash, never null or blank
     */
    public String commitHash() {
        return (commitHash != null && !commitHash.isBlank()) ? commitHash : "unknown";
    }
}

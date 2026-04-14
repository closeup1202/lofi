package io.github.closeup1202.lofi.backend.api.exception;

public class CommitNotFoundException extends RuntimeException {

    public CommitNotFoundException(String commitHash) {
        super("No metrics found for commit: " + commitHash);
    }
}

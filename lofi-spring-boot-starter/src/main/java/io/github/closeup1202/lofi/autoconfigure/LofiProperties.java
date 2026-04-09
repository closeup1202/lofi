package io.github.closeup1202.lofi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lofi")
public class LofiProperties {

    private String commitHash = "unknown";
    private Buffer buffer = new Buffer();

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public void setBuffer(Buffer buffer) {
        this.buffer = buffer;
    }

    public static class Buffer {
        private int flushThreshold = 100;
        private long flushDelayMs = 5000;

        public int getFlushThreshold() {
            return flushThreshold;
        }

        public void setFlushThreshold(int flushThreshold) {
            this.flushThreshold = flushThreshold;
        }

        public long getFlushDelayMs() {
            return flushDelayMs;
        }

        public void setFlushDelayMs(long flushDelayMs) {
            this.flushDelayMs = flushDelayMs;
        }
    }
}

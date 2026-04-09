package io.github.closeup1202.lofi.collector.context;

import org.springframework.beans.factory.annotation.Value;

/**
 * 커밋 해시는 두 가지 방법으로 주입
 * (1) application.yml에 직접 설정
 * (2) 배포 환경에서 GIT_COMMIT_HASH 환경변수로 주입
 * (X) 둘 다 없으면 unknown으로 fallback
 */
public class DeployContext {

    private final String commitHash;

    public DeployContext(
            @Value("${lofi.commit-hash:${GIT_COMMIT_HASH:unknown}}") String commitHash
    ) {
        this.commitHash = commitHash;
    }

    public String getCommitHash() {
        return commitHash;
    }
}

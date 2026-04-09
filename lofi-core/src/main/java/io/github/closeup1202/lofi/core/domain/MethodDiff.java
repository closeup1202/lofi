package io.github.closeup1202.lofi.core.domain;

/**
 *
 * @param signature
 * @param baseMs 비교 기준이 되는 배포의 평균 latency
 * @param headMs 비교 대상이 되는 배포의 평균 latency
 * @param deltaMs 배포 평균 차이값 headMs - baseMs = 양수면 느려진 것이고 음수면 빨라진 것
 * @param regressed 이 메서드가 성능이 나빠졌는지 플래그 필드 (deltaMs / baseMs > 0.2 처럼 20% 이상 증가했을 때)
 */
public record MethodDiff(
        String signature,
        double baseMs,
        double headMs,
        double deltaMs,
        boolean regressed
) {
}
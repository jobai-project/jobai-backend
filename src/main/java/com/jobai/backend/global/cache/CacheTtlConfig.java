package com.jobai.backend.global.cache;

import java.time.Duration;

/**
 * 캐시별 L1(Caffeine) / L2(Redis) TTL 및 용량 설정.
 *
 * @param name      캐시 이름 ({@link CacheNames} 상수 사용)
 * @param l1Ttl     L1 TTL (null이면 L1 비활성)
 * @param l1MaxSize L1 최대 엔트리 수 (0이면 L1 비활성)
 * @param l2Ttl     L2 TTL
 */
public record CacheTtlConfig(
        String name,
        Duration l1Ttl,
        long l1MaxSize,
        Duration l2Ttl
) {

    /** L1 캐시가 활성화되어 있는지 여부를 반환한다. */
    public boolean hasL1() {
        return l1Ttl != null && l1MaxSize > 0;
    }
}

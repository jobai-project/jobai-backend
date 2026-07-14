package com.jobai.backend.domain.bloom.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 테크 카드 중복 수집 방지를 위한 Redis Bloom Filter 서비스.
 * <p>externalId를 키로 사용하여 이미 수집된 기사를 필터링한다.
 * 프로파일 조건에 따라 빈으로 등록되지 않을 수 있으며,
 * 이 경우 {@link TechCardCollectService}에서 Optional로 처리한다.</p>
 */
@Service
@RequiredArgsConstructor
@Profile("!classify & !export & !collect & !local")
public class TechCardBloomFilterService {

    private static final String BLOOM_FILTER_NAME = "techcard:bloom";
    private static final long EXPECTED_INSERTIONS = 100_000L;
    private static final double FALSE_PROBABILITY = 0.01;

    private final RedissonClient redissonClient;
    private RBloomFilter<String> bloomFilter;

    @PostConstruct
    private void initBloomFilter() {
        bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_NAME);
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
    }

    public boolean mightContain(String key) {
        validateKey(key);
        return bloomFilter.contains(key);
    }

    public boolean add(String key) {
        validateKey(key);
        return bloomFilter.add(key);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
    }
}

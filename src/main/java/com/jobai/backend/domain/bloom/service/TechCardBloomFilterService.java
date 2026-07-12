package com.jobai.backend.domain.bloom.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Profile("!classify & !export & !local")
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

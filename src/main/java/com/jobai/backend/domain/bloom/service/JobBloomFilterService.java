package com.jobai.backend.domain.bloom.service;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobBloomFilterService {

    private static final String JOB_BLOOM_FILTER_NAME = "job:posting:bloom";
    private static final long EXPECTED_INSERTIONS = 1_000_000L;
    private static final double FALSE_PROBABILITY = 0.01;

    private final RedissonClient redissonClient;

    public boolean mightContain(String jobKey) {
        return getBloomFilter().contains(jobKey);
    }

    public boolean add(String jobKey) {
        return getBloomFilter().add(jobKey);
    }

    private RBloomFilter<String> getBloomFilter() {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(JOB_BLOOM_FILTER_NAME);
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        return bloomFilter;
    }
}

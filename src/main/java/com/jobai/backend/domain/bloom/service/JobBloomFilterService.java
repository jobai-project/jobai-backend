package com.jobai.backend.domain.bloom.service;

import jakarta.annotation.PostConstruct;
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
    private RBloomFilter<String> bloomFilter;

    @PostConstruct
    private void initBloomFilter() {
        bloomFilter = redissonClient.getBloomFilter(JOB_BLOOM_FILTER_NAME);
        bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
    }

    public boolean mightContain(String jobKey) {
        validateJobKey(jobKey);
        return bloomFilter.contains(jobKey);
    }

    public boolean add(String jobKey) {
        validateJobKey(jobKey);
        return bloomFilter.add(jobKey);
    }

    private void validateJobKey(String jobKey) {
        if (jobKey == null || jobKey.isBlank()) {
            throw new IllegalArgumentException("jobKey must not be null or blank");
        }
    }
}

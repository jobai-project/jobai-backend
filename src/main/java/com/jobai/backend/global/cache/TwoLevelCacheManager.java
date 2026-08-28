package com.jobai.backend.global.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L1(Caffeine) + L2(Redis) 2단계 캐시 매니저.
 *
 * <p>각 캐시 이름에 대해 {@link CacheTtlConfig}에 정의된 TTL/용량으로
 * {@link TwoLevelCache} 인스턴스를 생성한다.</p>
 */
public class TwoLevelCacheManager implements CacheManager {

    private final Map<String, TwoLevelCache> caches = new ConcurrentHashMap<>();
    private final Map<String, CacheTtlConfig> configMap;
    private final RedisCacheWriter cacheWriter;
    private final MeterRegistry meterRegistry;

    public TwoLevelCacheManager(RedisConnectionFactory connectionFactory, MeterRegistry meterRegistry) {
        this.cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(1000));
        this.meterRegistry = meterRegistry;
        this.configMap = buildConfigMap();
    }

    @Override
    public Cache getCache(String name) {
        CacheTtlConfig config = configMap.get(name);
        if (config == null) {
            return null;
        }
        return caches.computeIfAbsent(name, n ->
                new TwoLevelCache(n, config, cacheWriter, buildRedisConfig(config), meterRegistry));
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(configMap.keySet());
    }

    private RedisCacheConfiguration buildRedisConfig(CacheTtlConfig config) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(config.l2Ttl())
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(mapper)))
                .prefixCacheNameWith("jobai:cache:");
    }

    private static Map<String, CacheTtlConfig> buildConfigMap() {
        Map<String, CacheTtlConfig> map = new LinkedHashMap<>();

        // 공개 API — 모든 사용자 공유, L1+L2
        map.put(CacheNames.LATEST_JOBS, new CacheTtlConfig(
                CacheNames.LATEST_JOBS,
                Duration.ofMinutes(1), 500,
                Duration.ofHours(24)));

        map.put(CacheNames.NEW_JOBS_CARD, new CacheTtlConfig(
                CacheNames.NEW_JOBS_CARD,
                Duration.ofMinutes(30), 10,
                Duration.ofHours(24)));

        // 사용자별 API — L2 only
        map.put(CacheNames.RECOMMENDED_JOBS, new CacheTtlConfig(
                CacheNames.RECOMMENDED_JOBS,
                null, 0,
                Duration.ofHours(24)));

        map.put(CacheNames.JOB_SEARCH, new CacheTtlConfig(
                CacheNames.JOB_SEARCH,
                null, 0,
                Duration.ofHours(24)));

        // 검색 파이프라인 내부 — L2 only
        map.put(CacheNames.QUERY_EXPANSION, new CacheTtlConfig(
                CacheNames.QUERY_EXPANSION,
                null, 0,
                Duration.ofHours(24)));

        map.put(CacheNames.QUERY_EMBEDDING, new CacheTtlConfig(
                CacheNames.QUERY_EMBEDDING,
                null, 0,
                Duration.ofHours(24)));

        return Collections.unmodifiableMap(map);
    }
}

package com.jobai.backend.global.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.util.concurrent.Callable;

/**
 * L1(Caffeine) + L2(Redis) Look-through 캐시.
 *
 * <pre>
 * get(key):
 *   1. L1 확인 → HIT → 반환
 *   2. L2 확인 → HIT → L1 적재 → 반환
 *   3. MISS → null 반환 (Spring이 원본 메서드 호출 후 put)
 *
 * put(key, value):
 *   1. L2 저장
 *   2. L1 저장 (L1 활성 시)
 *
 * evict(key):
 *   1. L1 제거
 *   2. L2 제거
 * </pre>
 */
@Slf4j
public class TwoLevelCache implements org.springframework.cache.Cache {

    private final String name;
    private final Cache<Object, Object> caffeineCache;
    private final RedisCache redisCache;
    private final Counter l2HitCounter;
    private final Counter l2MissCounter;

    public TwoLevelCache(String name,
                         CacheTtlConfig config,
                         RedisCacheWriter cacheWriter,
                         RedisCacheConfiguration redisConfig,
                         MeterRegistry meterRegistry) {
        this.name = name;

        if (config.hasL1()) {
            this.caffeineCache = Caffeine.newBuilder()
                    .maximumSize(config.l1MaxSize())
                    .expireAfterWrite(config.l1Ttl())
                    .recordStats()
                    .build();
            CaffeineCacheMetrics.monitor(meterRegistry, this.caffeineCache, name);
        } else {
            this.caffeineCache = null;
        }

        this.redisCache = new InternalRedisCache(name, cacheWriter, redisConfig);

        this.l2HitCounter = Counter.builder("cache.l2.requests")
                .tag("cache", name)
                .tag("result", "hit")
                .register(meterRegistry);
        this.l2MissCounter = Counter.builder("cache.l2.requests")
                .tag("cache", name)
                .tag("result", "miss")
                .register(meterRegistry);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public ValueWrapper get(Object key) {
        // L1 확인
        if (caffeineCache != null) {
            Object l1Value = caffeineCache.getIfPresent(key);
            if (l1Value != null) {
                return () -> l1Value;
            }
        }

        // L2 확인
        ValueWrapper l2Result = redisCache.get(key);
        if (l2Result != null) {
            l2HitCounter.increment();
            if (caffeineCache != null) {
                caffeineCache.put(key, l2Result.get());
            }
            return l2Result;
        }

        l2MissCounter.increment();
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException(
                    "캐시 '" + name + "'에서 키 '" + key + "'의 값이 [" + type.getName() + "] 타입이 아닙니다: " + value.getClass().getName());
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }

        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        redisCache.put(key, value);
        if (caffeineCache != null && value != null) {
            caffeineCache.put(key, value);
        }
    }

    @Override
    public void evict(Object key) {
        redisCache.evict(key);
        if (caffeineCache != null) {
            caffeineCache.invalidate(key);
        }
    }

    @Override
    public void clear() {
        redisCache.clear();
        if (caffeineCache != null) {
            caffeineCache.invalidateAll();
        }
        log.info("[Cache] '{}' 캐시 전체 무효화 완료", name);
    }

    /** {@link RedisCache}의 protected 생성자에 접근하기 위한 내부 서브클래스. */
    private static class InternalRedisCache extends RedisCache {
        InternalRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration config) {
            super(name, cacheWriter, config);
        }
    }
}

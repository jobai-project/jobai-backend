package com.jobai.backend.global.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** L1(Caffeine) + L2(Redis) 2단계 캐시 매니저를 등록하는 설정. */
@Configuration
@EnableCaching
@Profile("!classify & !export & !collect")
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                     MeterRegistry meterRegistry) {
        return new TwoLevelCacheManager(redisConnectionFactory, meterRegistry);
    }
}

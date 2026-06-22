package com.jobai.backend.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${redis.host}") String redisHost,
            @Value("${redis.port}") int redisPort
    ) {
        Config config = new Config();

        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectTimeout((int) Duration.ofSeconds(5).toMillis())
                .setTimeout((int) Duration.ofSeconds(3).toMillis());

        return Redisson.create(config);
    }
}

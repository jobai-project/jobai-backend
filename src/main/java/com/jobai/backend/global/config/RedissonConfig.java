package com.jobai.backend.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@Profile("!classify & !export & !collect & !local")
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${redis.host}") String redisHost,
            @Value("${redis.port}") int redisPort,
            @Value("${redis.password:}") String redisPassword
    ) {
        Config config = new Config();

        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectTimeout((int) Duration.ofSeconds(5).toMillis())
                .setTimeout((int) Duration.ofSeconds(3).toMillis());

        if (StringUtils.hasText(redisPassword)) {
            config.useSingleServer().setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}

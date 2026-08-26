package com.jobai.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

@Configuration
@Profile("kafka")
public class KafkaRedisConfig {

    /** Kafka 프로필용 Redis 연결 팩토리. 비밀번호가 설정된 경우 인증을 수행한다. */
    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${redis.host:localhost}") String redisHost,
            @Value("${redis.port:6379}") int redisPort,
            @Value("${redis.password:}") String redisPassword
    ) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (StringUtils.hasText(redisPassword)) {
            configuration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(configuration);
    }

    /** Kafka 완료 추적용 StringRedisTemplate. */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}

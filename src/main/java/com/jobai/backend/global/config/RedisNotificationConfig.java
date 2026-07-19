package com.jobai.backend.global.config;

import com.jobai.backend.domain.notification.service.RedisNotificationSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

@Configuration
@Profile("!classify & !export & !collect & !local & !test")
public class RedisNotificationConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${redis.host}") String redisHost,
            @Value("${redis.port}") int redisPort,
            @Value("${redis.password:}") String redisPassword
    ) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (StringUtils.hasText(redisPassword)) {
            configuration.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    public StringRedisTemplate notificationStringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            RedisNotificationSubscriber redisNotificationSubscriber,
            ThreadPoolTaskExecutor notificationRedisTaskExecutor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(notificationRedisTaskExecutor);
        container.setSubscriptionExecutor(notificationRedisTaskExecutor);
        container.addMessageListener(redisNotificationSubscriber, new PatternTopic("notification:*"));
        return container;
    }

    @Bean
    public ThreadPoolTaskExecutor notificationRedisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-redis-");
        executor.initialize();
        return executor;
    }
}

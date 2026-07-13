package com.jobai.backend.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!classify & !export & !collect & !local & !test")
public class RedisNotificationPublisher {

    private static final String CHANNEL_PREFIX = "notification:";

    @Qualifier("notificationStringRedisTemplate")
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String userId, RealtimeNotificationPayload payload) {
        if (!StringUtils.hasText(userId)) {
            log.warn("Skip notification publish because userId is blank");
            return;
        }

        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + userId, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }
    }
}

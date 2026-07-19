package com.jobai.backend.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import com.jobai.backend.domain.notification.util.NotificationLogUtils;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!classify & !export & !collect & !local & !test")
public class RedisNotificationSubscriber implements MessageListener {

    private static final String CHANNEL_PREFIX = "notification:";

    private final ObjectMapper objectMapper;
    private final WebSocketNotificationService webSocketNotificationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        handleMessage(body, channel);
    }

    public void handleMessage(String message, String channel) {
        String userId = extractUserId(channel);
        if (!StringUtils.hasText(userId)) {
            log.warn("Skip notification message from invalid channel");
            return;
        }

        try {
            RealtimeNotificationPayload payload = objectMapper.readValue(message, RealtimeNotificationPayload.class);
            log.info("[실시간알림] Redis 구독 수신: user={}", NotificationLogUtils.maskUserId(userId));
            webSocketNotificationService.sendToUser(userId, payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize notification payload", e);
        }
    }

    private String extractUserId(String channel) {
        if (!StringUtils.hasText(channel) || !channel.startsWith(CHANNEL_PREFIX)) {
            return null;
        }
        return channel.substring(CHANNEL_PREFIX.length());
    }
}

package com.jobai.backend.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!classify & !export & !collect & !local & !test")
public class RedisNotificationSubscriber {

    private static final String CHANNEL_PREFIX = "notification:";

    private final ObjectMapper objectMapper;
    private final WebSocketNotificationService webSocketNotificationService;

    public void handleMessage(String message, String channel) {
        String userId = extractUserId(channel);
        if (!StringUtils.hasText(userId)) {
            log.warn("Skip notification message from invalid channel: {}", channel);
            return;
        }

        try {
            RealtimeNotificationPayload payload = objectMapper.readValue(message, RealtimeNotificationPayload.class);
            webSocketNotificationService.sendToUser(userId, payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize notification payload from channel {}", channel, e);
        }
    }

    private String extractUserId(String channel) {
        if (!StringUtils.hasText(channel) || !channel.startsWith(CHANNEL_PREFIX)) {
            return null;
        }
        return channel.substring(CHANNEL_PREFIX.length());
    }
}

package com.jobai.backend.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!classify & !export & !collect")
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendToUser(String userId, Object payload) {
        log.info("[실시간알림] WebSocket 사용자 큐 전송: user={}, destination=/user/queue/notifications", maskUserId(userId));
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                payload
        );
    }

    private String maskUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return "";
        }
        int atIndex = userId.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return userId.charAt(0) + "***" + userId.substring(atIndex);
    }
}

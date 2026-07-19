package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.notification.util.NotificationLogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!classify & !export & !collect")
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendToUser(String userId, Object payload) {
        log.info("[실시간알림] WebSocket 사용자 큐 전송: user={}, destination=/user/queue/notifications",
                NotificationLogUtils.maskUserId(userId));
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                payload
        );
    }
}

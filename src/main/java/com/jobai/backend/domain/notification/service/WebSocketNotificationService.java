package com.jobai.backend.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendToUser(String userId, Object payload) {
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/notifications",
                payload
        );
    }
}

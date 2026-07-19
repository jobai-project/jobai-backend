package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<RedisNotificationPublisher> redisNotificationPublisher;
    private final EmailNotificationService emailNotificationService;
    private final WebhookNotificationService webhookNotificationService;

    public void notifyUser(String userId, RealtimeNotificationPayload payload) {
        RedisNotificationPublisher publisher = redisNotificationPublisher.getIfAvailable();
        if (publisher == null) {
            log.warn("[실시간알림] Redis publisher bean is not available");
        } else {
            publishRealtimeNotification(publisher, userId, payload);
        }

        notificationRepository.findByMemberEmail(userId)
                .ifPresent(notification -> {
                    sendEmail(userId, notification, payload);
                    sendWebhooks(notification, payload);
                });
    }

    private void publishRealtimeNotification(
            RedisNotificationPublisher publisher,
            String userId,
            RealtimeNotificationPayload payload
    ) {
        try {
            publisher.publish(userId, payload);
        } catch (RuntimeException e) {
            log.warn("Realtime notification publish failed", e);
        }
    }

    private void sendWebhooks(Notification notification, RealtimeNotificationPayload payload) {
        if (Boolean.TRUE.equals(notification.getSlackNotification())
                && StringUtils.hasText(notification.getSlackWebhookUrl())) {
            webhookNotificationService.sendSlack(notification.getSlackWebhookUrl(), payload);
        }

        if (Boolean.TRUE.equals(notification.getDiscordNotification())
                && StringUtils.hasText(notification.getDiscordWebhookUrl())) {
            webhookNotificationService.sendDiscord(notification.getDiscordWebhookUrl(), payload);
        }
    }

    private void sendEmail(String userId, Notification notification, RealtimeNotificationPayload payload) {
        if (!Boolean.TRUE.equals(notification.getEmailNotification())) {
            return;
        }

        try {
            emailNotificationService.send(userId, payload);
        } catch (RuntimeException e) {
            log.warn("Email notification failed", e);
        }
    }
}

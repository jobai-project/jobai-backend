package com.jobai.backend.global.kafka.consumer;

import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import com.jobai.backend.domain.notification.service.NotificationDispatchService;
import com.jobai.backend.global.kafka.event.NotificationDispatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaNotificationConsumer {

    private final NotificationDispatchService notificationDispatchService;

    @KafkaListener(
            topics = "jobai.notification.dispatch",
            groupId = "jobai-notification-group",
            properties = {
                    "spring.json.value.default.type=com.jobai.backend.global.kafka.event.NotificationDispatchEvent"
            }
    )
    public void consume(NotificationDispatchEvent event) {
        log.info("[Kafka] 알림 이벤트 수신: userId={}, type={}", event.userId(), event.type());

        notificationDispatchService.notifyUser(
                event.userId(),
                RealtimeNotificationPayload.of(
                        event.type(),
                        event.title(),
                        event.message(),
                        event.linkUrl(),
                        event.createdAt()
                )
        );

        log.info("[Kafka] 알림 처리 완료: userId={}", event.userId());
    }
}

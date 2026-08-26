package com.jobai.backend.global.kafka.consumer;

import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import com.jobai.backend.domain.notification.service.NotificationDispatchService;
import com.jobai.backend.global.kafka.event.NotificationDispatchEvent;
import com.jobai.backend.global.util.LogMaskingUtil;
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

    /**
     * 알림 발송 이벤트를 수신하여 NotificationDispatchService로 전달한다.
     * 예외를 catch하여 재처리로 인한 중복 발송(이메일 등)을 방지한다.
     */
    @KafkaListener(
            topics = "jobai.notification.dispatch",
            groupId = "jobai-notification-group",
            properties = {
                    "spring.json.value.default.type=com.jobai.backend.global.kafka.event.NotificationDispatchEvent"
            }
    )
    public void consume(NotificationDispatchEvent event) {
        log.info("[Kafka] 알림 이벤트 수신: userId={}, type={}", LogMaskingUtil.maskEmail(event.userId()), event.type());

        try {
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
            log.info("[Kafka] 알림 처리 완료: userId={}", LogMaskingUtil.maskEmail(event.userId()));
        } catch (Exception e) {
            // 예외를 전파하면 DefaultErrorHandler가 재처리하여 이메일이 중복 발송될 수 있으므로 로그만 남긴다
            log.error("[Kafka] 알림 처리 실패 (재시도 안 함): userId={}, error={}",
                    LogMaskingUtil.maskEmail(event.userId()), e.getMessage(), e);
        }
    }

}

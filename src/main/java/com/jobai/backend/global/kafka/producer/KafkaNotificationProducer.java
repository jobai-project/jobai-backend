package com.jobai.backend.global.kafka.producer;

import com.jobai.backend.global.kafka.event.NotificationDispatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaNotificationProducer {

    private static final String TOPIC = "jobai.notification.dispatch";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** 알림 이벤트를 Kafka 토픽에 비동기 발행한다. key는 userId(email). */
    public void send(NotificationDispatchEvent event) {
        kafkaTemplate.send(TOPIC, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] 알림 이벤트 발행 실패: userId={}, error={}",
                                maskEmail(event.userId()), ex.getMessage());
                    } else {
                        log.debug("[Kafka] 알림 이벤트 발행 완료: userId={}, partition={}, offset={}",
                                maskEmail(event.userId()),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }
}

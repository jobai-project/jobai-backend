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

    public void send(NotificationDispatchEvent event) {
        kafkaTemplate.send(TOPIC, event.userId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] 알림 이벤트 발행 실패: userId={}, error={}",
                                event.userId(), ex.getMessage());
                    } else {
                        log.info("[Kafka] 알림 이벤트 발행 완료: userId={}, partition={}, offset={}",
                                event.userId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}

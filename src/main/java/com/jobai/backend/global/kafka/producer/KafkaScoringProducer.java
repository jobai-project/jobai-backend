package com.jobai.backend.global.kafka.producer;

import com.jobai.backend.global.kafka.event.ScoringRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaScoringProducer {

    private static final String TOPIC = "jobai.scoring.request";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(ScoringRequestEvent event) {
        String key = event.resumeId() + ":" + event.postingId();
        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] 스코어링 요청 발행 실패: key={}, error={}", key, ex.getMessage());
                    }
                });
    }
}

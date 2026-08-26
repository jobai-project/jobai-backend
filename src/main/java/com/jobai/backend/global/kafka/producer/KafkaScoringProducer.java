package com.jobai.backend.global.kafka.producer;

import com.jobai.backend.global.kafka.event.ScoringRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaScoringProducer {

    private static final String TOPIC = "jobai.scoring.request";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 스코어링 요청 이벤트를 Kafka 토픽에 비동기 발행한다.
     * 발행 실패 시 SADD로 멱등하게 completed/failed 카운터를 증가시켜 완료 판정이 깨지지 않도록 한다.
     */
    public void send(ScoringRequestEvent event) {
        String key = event.resumeId() + ":" + event.postingId();
        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] 스코어링 요청 발행 실패: key={}, error={}", key, ex.getMessage());
                        if (event.pipelineRunId() != null) {
                            String prefix = "jobai:scoring:" + event.pipelineRunId();
                            // SADD로 멱등성 보장 (Consumer 측과 동일 패턴)
                            Long added = stringRedisTemplate.opsForSet().add(prefix + ":processed", key);
                            if (added != null && added > 0) {
                                stringRedisTemplate.expire(prefix + ":processed", Duration.ofHours(24));
                                stringRedisTemplate.opsForValue().increment(prefix + ":completed");
                                stringRedisTemplate.expire(prefix + ":completed", Duration.ofHours(24));
                                stringRedisTemplate.opsForValue().increment(prefix + ":failed");
                                stringRedisTemplate.expire(prefix + ":failed", Duration.ofHours(24));
                            }
                        }
                    }
                });
    }
}

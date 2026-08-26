package com.jobai.backend.global.kafka.producer;

import com.jobai.backend.global.kafka.event.PipelineStageCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaPipelineProducer {

    private static final String TOPIC_PREFIX = "jobai.pipeline.";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendStageComplete(PipelineStageCompleteEvent event) {
        String topic = TOPIC_PREFIX + event.stage().toLowerCase() + "-complete";
        kafkaTemplate.send(topic, event.pipelineRunId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka 파이프라인] {} 완료 이벤트 발행 실패: {}",
                                event.stage(), ex.getMessage());
                    } else {
                        log.info("[Kafka 파이프라인] {} 완료 이벤트 발행: pipelineRunId={}, 처리={}건",
                                event.stage(), event.pipelineRunId(), event.processedCount());
                    }
                });
    }
}

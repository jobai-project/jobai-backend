package com.jobai.backend.global.kafka.producer;

import com.jobai.backend.global.kafka.event.PipelineStageCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaPipelineProducer {

    private static final String TOPIC_PREFIX = "jobai.pipeline.";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 파이프라인 단계 완료 이벤트를 해당 단계의 토픽에 발행한다.
     * 반환된 Future로 발행 성공/실패를 호출자가 확인할 수 있다.
     */
    public CompletableFuture<Void> sendStageComplete(PipelineStageCompleteEvent event) {
        String topic = TOPIC_PREFIX + event.stage().toLowerCase() + "-complete";
        return kafkaTemplate.send(topic, event.pipelineRunId(), event)
                .thenAccept(result ->
                        log.info("[Kafka 파이프라인] {} 완료 이벤트 발행: pipelineRunId={}, 처리={}건",
                                event.stage(), event.pipelineRunId(), event.processedCount()))
                .whenComplete((ignored, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka 파이프라인] {} 완료 이벤트 발행 실패: {}",
                                event.stage(), ex.getMessage());
                    }
                });
    }
}

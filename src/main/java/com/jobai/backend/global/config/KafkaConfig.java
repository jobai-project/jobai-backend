package com.jobai.backend.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Profile("kafka")
public class KafkaConfig {

    // ── 알림 토픽 ──────────────────────────────────────────
    @Bean
    public NewTopic notificationDispatchTopic() {
        return TopicBuilder.name("jobai.notification.dispatch")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationDispatchDltTopic() {
        return TopicBuilder.name("jobai.notification.dispatch.DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ── 스코어링 토픽 ────────────────────────────────────────
    @Bean
    public NewTopic scoringRequestTopic() {
        return TopicBuilder.name("jobai.scoring.request")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic scoringRequestDltTopic() {
        return TopicBuilder.name("jobai.scoring.request.DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic scoringResultTopic() {
        return TopicBuilder.name("jobai.scoring.result")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── 파이프라인 토픽 ──────────────────────────────────────
    @Bean
    public NewTopic collectionCompleteTopic() {
        return TopicBuilder.name("jobai.pipeline.collection-complete")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic classificationCompleteTopic() {
        return TopicBuilder.name("jobai.pipeline.classification-complete")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic embeddingCompleteTopic() {
        return TopicBuilder.name("jobai.pipeline.embedding-complete")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ── 에러 처리: 3회 재시도 후 DLT로 전송 ─────────────────
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaOperations<String, Object> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3L)
        );

        errorHandler.addNotRetryableExceptions(
                DeserializationException.class,
                IllegalArgumentException.class
        );

        return errorHandler;
    }
}

package com.jobai.backend.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;

@Configuration
@Profile("kafka")
public class KafkaConfig {

    // ── 알림 토픽 ──────────────────────────────────────────
    /** 알림 발송 토픽 (3 파티션). */
    @Bean
    public NewTopic notificationDispatchTopic() {
        return TopicBuilder.name("jobai.notification.dispatch")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** 알림 발송 DLT (실패 메시지 보관). */
    @Bean
    public NewTopic notificationDispatchDltTopic() {
        return TopicBuilder.name("jobai.notification.dispatch.DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ── 스코어링 토픽 ────────────────────────────────────────
    /** 스코어링 요청 토픽 (6 파티션 = 최대 6 Consumer 병렬 처리). */
    @Bean
    public NewTopic scoringRequestTopic() {
        return TopicBuilder.name("jobai.scoring.request")
                .partitions(6)
                .replicas(1)
                .build();
    }

    /** 스코어링 요청 DLT (실패 메시지 보관). */
    @Bean
    public NewTopic scoringRequestDltTopic() {
        return TopicBuilder.name("jobai.scoring.request.DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /** 스코어링 결과 토픽 (3 파티션). */
    @Bean
    public NewTopic scoringResultTopic() {
        return TopicBuilder.name("jobai.scoring.result")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── 파이프라인 토픽 ──────────────────────────────────────
    /** 수집 완료 파이프라인 이벤트 토픽. */
    @Bean
    public NewTopic collectionCompleteTopic() {
        return TopicBuilder.name("jobai.pipeline.collection-complete")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /** 분류 완료 파이프라인 이벤트 토픽. */
    @Bean
    public NewTopic classificationCompleteTopic() {
        return TopicBuilder.name("jobai.pipeline.classification-complete")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /** 임베딩 완료 파이프라인 이벤트 토픽. */
    @Bean
    public NewTopic embeddingCompleteTopic() {
        return TopicBuilder.name("jobai.pipeline.embedding-complete")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Kafka Consumer 메시지 수신 시 헤더에서 requestId를 꺼내 MDC에 세팅한다.
     * 처리 완료 후 afterRecord에서 MDC를 정리한다.
     */
    @Bean
    public RecordInterceptor<String, Object> mdcRecordInterceptor() {
        return new RecordInterceptor<>() {
            private static final String HEADER_NAME = "X-Request-ID";
            private static final String MDC_KEY = "requestId";

            @Override
            public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record,
                                                            Consumer<String, Object> consumer) {
                Header header = record.headers().lastHeader(HEADER_NAME);
                if (header != null) {
                    MDC.put(MDC_KEY, new String(header.value(), StandardCharsets.UTF_8));
                }
                return record;
            }

            @Override
            public void afterRecord(ConsumerRecord<String, Object> record,
                                    Consumer<String, Object> consumer) {
                MDC.remove(MDC_KEY);
            }
        };
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            KafkaProperties kafkaProperties,
            CommonErrorHandler kafkaErrorHandler) {
        ConsumerFactory<String, Object> consumerFactory =
                new DefaultKafkaConsumerFactory<>(kafkaProperties.buildConsumerProperties());
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
        factory.getContainerProperties().setAckMode(kafkaProperties.getListener().getAckMode());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setRecordInterceptor(mdcRecordInterceptor());
        return factory;
    }

    /**
     * Kafka Consumer 공통 에러 핸들러.
     * 3회 재시도(1초 간격) 후 실패 시 DLT로 전송한다.
     */
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

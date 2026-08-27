package com.jobai.backend.global.config;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MdcKafkaProducerInterceptor implements ProducerInterceptor<String, Object> {

    private static final String MDC_KEY = "requestId";
    private static final String HEADER_NAME = "X-Request-ID";

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        String requestId = MDC.get(MDC_KEY);
        if (requestId != null) {
            record.headers().add(HEADER_NAME, requestId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }
}

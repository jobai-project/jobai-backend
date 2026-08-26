package com.jobai.backend.global.kafka.consumer;

import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import com.jobai.backend.domain.notification.service.NotificationDispatchService;
import com.jobai.backend.global.kafka.event.NotificationDispatchEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KafkaNotificationConsumerTest {

    private NotificationDispatchService notificationDispatchService;
    private KafkaNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        notificationDispatchService = Mockito.mock(NotificationDispatchService.class);
        consumer = new KafkaNotificationConsumer(notificationDispatchService);
    }

    @Test
    @DisplayName("이벤트 수신 시 NotificationDispatchService.notifyUser()가 호출된다")
    void consume_이벤트수신_notifyUser호출() {
        Instant now = Instant.now();
        NotificationDispatchEvent event = new NotificationDispatchEvent(
                "user@test.com", "MATCH", "새 추천 공고",
                "[테스트회사] 백엔드 개발자 (매칭 85점)",
                "/notifications/matches/123", now
        );

        consumer.consume(event);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RealtimeNotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(RealtimeNotificationPayload.class);
        verify(notificationDispatchService).notifyUser(emailCaptor.capture(), payloadCaptor.capture());

        assertThat(emailCaptor.getValue()).isEqualTo("user@test.com");
        RealtimeNotificationPayload payload = payloadCaptor.getValue();
        assertThat(payload.type()).isEqualTo("MATCH");
        assertThat(payload.title()).isEqualTo("새 추천 공고");
        assertThat(payload.message()).isEqualTo("[테스트회사] 백엔드 개발자 (매칭 85점)");
        assertThat(payload.linkUrl()).isEqualTo("/notifications/matches/123");
        assertThat(payload.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("notifyUser 예외 시 재시도 없이 로그만 남긴다 (중복 발송 방지)")
    void consume_예외시_재시도없이_로그() {
        NotificationDispatchEvent event = new NotificationDispatchEvent(
                "user@test.com", "MATCH", "새 추천 공고",
                "메시지", "/link", Instant.now()
        );

        doThrow(new RuntimeException("웹훅 실패"))
                .when(notificationDispatchService).notifyUser(anyString(), any());

        // 예외가 전파되지 않아야 함 (Kafka 재시도 방지)
        consumer.consume(event);

        verify(notificationDispatchService).notifyUser(anyString(), any());
    }
}

package com.jobai.backend.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisNotificationSubscriberTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    @Test
    void routesRedisNotificationToUserQueue() throws Exception {
        RedisNotificationSubscriber subscriber = new RedisNotificationSubscriber(objectMapper, webSocketNotificationService);
        RealtimeNotificationPayload payload = RealtimeNotificationPayload.of("MATCH", "title", "message", "https://jobai.site");
        String message = objectMapper.writeValueAsString(payload);

        subscriber.handleMessage(message, "notification:user@example.com");

        ArgumentCaptor<RealtimeNotificationPayload> payloadCaptor = ArgumentCaptor.forClass(RealtimeNotificationPayload.class);
        verify(webSocketNotificationService).sendToUser(eq("user@example.com"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().type()).isEqualTo("MATCH");
        assertThat(payloadCaptor.getValue().title()).isEqualTo("title");
    }

    @Test
    void ignoresInvalidChannel() {
        RedisNotificationSubscriber subscriber = new RedisNotificationSubscriber(objectMapper, webSocketNotificationService);

        subscriber.handleMessage("{}", "invalid:user@example.com");

        verify(webSocketNotificationService, never()).sendToUser(anyString(), any());
    }

    @Test
    void ignoresMalformedJsonPayload() {
        RedisNotificationSubscriber subscriber = new RedisNotificationSubscriber(objectMapper, webSocketNotificationService);

        subscriber.handleMessage("{malformed-json", "notification:user@example.com");

        verify(webSocketNotificationService, never()).sendToUser(anyString(), any());
    }
}

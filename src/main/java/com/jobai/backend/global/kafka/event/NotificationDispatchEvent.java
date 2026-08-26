package com.jobai.backend.global.kafka.event;

import java.time.Instant;

public record NotificationDispatchEvent(
        String userId,
        String type,
        String title,
        String message,
        String linkUrl,
        Instant createdAt
) {
}

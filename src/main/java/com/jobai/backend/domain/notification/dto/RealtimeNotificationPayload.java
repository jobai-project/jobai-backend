package com.jobai.backend.domain.notification.dto;

import java.time.Instant;

public record RealtimeNotificationPayload(
        String type,
        String title,
        String message,
        String linkUrl,
        Instant createdAt
) {
    public static RealtimeNotificationPayload of(String type, String title, String message, String linkUrl) {
        return of(type, title, message, linkUrl, Instant.now());
    }

    public static RealtimeNotificationPayload of(
            String type,
            String title,
            String message,
            String linkUrl,
            Instant createdAt
    ) {
        return new RealtimeNotificationPayload(type, title, message, linkUrl, createdAt);
    }
}

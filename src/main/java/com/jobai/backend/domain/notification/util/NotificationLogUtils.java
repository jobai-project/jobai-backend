package com.jobai.backend.domain.notification.util;

import org.springframework.util.StringUtils;

public final class NotificationLogUtils {

    private NotificationLogUtils() {
    }

    public static String maskUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return "";
        }

        int atIndex = userId.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return userId.charAt(0) + "***" + userId.substring(atIndex);
    }
}

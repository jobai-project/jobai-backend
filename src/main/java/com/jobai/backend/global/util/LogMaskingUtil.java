package com.jobai.backend.global.util;

/**
 * 로그 출력 시 개인정보를 마스킹하는 유틸리티.
 */
public final class LogMaskingUtil {

    private LogMaskingUtil() {}

    /** 이메일 주소를 마스킹한다. 예: "user@example.com" → "u***@example.com" */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }
}

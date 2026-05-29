package com.jobai.backend.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieProvider {

    // 쿠키 이름을 상수로 관리하여 오타 방지
    public static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    private final boolean cookieSecure;

    // 생성자를 통해 환경 변수 주입
    public CookieProvider(@Value("${app.auth.cookie.secure:true}") boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /**
     * 로그인 성공 시 Access Token을 담을 쿠키 생성
     */
    public ResponseCookie createAccessTokenCookie(String accessToken) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, accessToken)
                .path("/")
                .httpOnly(true)                // JavaScript 접근 차단 (XSS 방어)
                .secure(cookieSecure)          // HTTPS 환경 여부 설정
                .sameSite("Lax")               // CSRF 방어
                .maxAge(Duration.ofHours(1))   // 쿠키 유효 기간 (1시간)
                .build();
    }

    /**
     * 로그아웃 시 기존 쿠키를 무효화(삭제)하기 위한 쿠키 생성
     */
    public ResponseCookie createEmptyAccessTokenCookie() {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .maxAge(0)                     // maxAge를 0으로 설정하여 브라우저가 즉시 삭제하도록 함
                .build();
    }
}
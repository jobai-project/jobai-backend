package com.jobai.backend.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieProvider {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    public static final String OAUTH2_FRONTEND_REDIRECT_COOKIE_NAME = "oauth2_frontend_redirect";

    private final boolean cookieSecure;
    private final String cookieSameSite;

    public CookieProvider(
            @Value("${app.auth.cookie.secure:true}") boolean cookieSecure,
            @Value("${app.auth.cookie.same-site:${APP_AUTH_COOKIE_SAME_SITE:Lax}}") String cookieSameSite
    ) {
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    public ResponseCookie createAccessTokenCookie(String accessToken) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, accessToken)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(Duration.ofHours(1))
                .build();
    }

    public ResponseCookie createEmptyAccessTokenCookie() {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .maxAge(0)
                .build();
    }

    public ResponseCookie createOAuthFrontendRedirectCookie(String frontendRedirectUri) {
        return ResponseCookie.from(OAUTH2_FRONTEND_REDIRECT_COOKIE_NAME, frontendRedirectUri)
                .path("/login/oauth2/code/google")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .maxAge(Duration.ofMinutes(5))
                .build();
    }

    public ResponseCookie createEmptyOAuthFrontendRedirectCookie() {
        return ResponseCookie.from(OAUTH2_FRONTEND_REDIRECT_COOKIE_NAME, "")
                .path("/login/oauth2/code/google")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }
}

package com.jobai.backend.global.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    @Value("${app.auth.cookie.secure:true}")
    private boolean cookieSecure;
    @Value("${app.auth.frontend-redirect-url}")
    private String frontendRedirectUrl;
    private final CookieProvider cookieProvider; // 공통 쿠키 컴포넌트 주입

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");

        log.debug("OAuth2 로그인 성공 유저 이메일: {}", email);

        // 1. 임시 토큰 생성
        String accessToken = jwtProvider.createAccessToken(email);

        // 2. 안전한 토큰 전달을 위한 HttpOnly 쿠키 생성
        ResponseCookie cookie = cookieProvider.createAccessTokenCookie(accessToken);

        // 3. 응답 헤더에 쿠키 세팅
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // 4. 프론트엔드 특정 페이지로 토큰을 들고 리디렉션 처리
        response.sendRedirect(frontendRedirectUrl);
    }
}

package com.jobai.backend.global.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final CookieProvider cookieProvider;
    private final FrontendRedirectUriResolver frontendRedirectUriResolver;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication
    ) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");
        String accessToken = jwtProvider.createAccessToken(email);
        String frontendRedirectUrl = frontendRedirectUriResolver.resolve(
                Arrays.stream(request.getCookies() == null ? new Cookie[0] : request.getCookies())
                        .filter(cookie -> CookieProvider.OAUTH2_FRONTEND_REDIRECT_COOKIE_NAME.equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse(null)
        );
        ResponseCookie accessTokenCookie = cookieProvider.createAccessTokenCookie(
                accessToken,
                frontendRedirectUriResolver.isLocalDevelopmentRedirect(frontendRedirectUrl)
        );

        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProvider.createEmptyOAuthFrontendRedirectCookie().toString());
        response.sendRedirect(frontendRedirectUrl);
    }
}

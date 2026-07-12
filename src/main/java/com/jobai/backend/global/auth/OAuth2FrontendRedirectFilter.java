package com.jobai.backend.global.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2FrontendRedirectFilter extends OncePerRequestFilter {

    public static final String FRONTEND_REDIRECT_URI_PARAMETER = "frontend_redirect_uri";
    private static final String GOOGLE_AUTHORIZATION_PATH = "/oauth2/authorization/google";

    private final FrontendRedirectUriResolver frontendRedirectUriResolver;
    private final CookieProvider cookieProvider;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod())
                || !(request.getContextPath() + GOOGLE_AUTHORIZATION_PATH).equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String frontendRedirectUri = request.getParameter(FRONTEND_REDIRECT_URI_PARAMETER);
        if (frontendRedirectUri != null) {
            if (!frontendRedirectUriResolver.isAllowed(frontendRedirectUri)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported frontend redirect URI");
                return;
            }
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    cookieProvider.createOAuthFrontendRedirectCookie(frontendRedirectUri).toString()
            );
        }
        filterChain.doFilter(request, response);
    }
}

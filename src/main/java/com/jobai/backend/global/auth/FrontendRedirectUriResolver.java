package com.jobai.backend.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class FrontendRedirectUriResolver {

    private final String defaultRedirectUri;
    private final Set<String> allowedRedirectUris;

    public FrontendRedirectUriResolver(
            @Value("${app.auth.frontend-redirect-url}") String defaultRedirectUri,
            @Value("${app.auth.allowed-frontend-redirect-urls:${APP_ALLOWED_FRONTEND_REDIRECT_URLS:}}") String configuredRedirectUris
    ) {
        this.defaultRedirectUri = defaultRedirectUri;
        this.allowedRedirectUris = new LinkedHashSet<>();
        this.allowedRedirectUris.add(defaultRedirectUri);
        Arrays.stream(configuredRedirectUris.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(this.allowedRedirectUris::add);
    }

    public boolean isAllowed(String redirectUri) {
        return redirectUri != null && allowedRedirectUris.contains(redirectUri);
    }

    public String resolve(String requestedRedirectUri) {
        return isAllowed(requestedRedirectUri) ? requestedRedirectUri : defaultRedirectUri;
    }
}

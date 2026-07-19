package com.jobai.backend.global.config;

import com.jobai.backend.global.auth.JwtProvider;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
//@Profile("!classify & !export")
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String WEBSOCKET_USER_EMAIL_ATTRIBUTE = "websocketUserEmail";

    private final JwtProvider jwtProvider;
    @Value("${websocket.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(parseAllowedOrigins())
                .addInterceptors(jwtCookieHandshakeInterceptor())
                .setHandshakeHandler(jwtCookieHandshakeHandler())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = extractToken(accessor);
                    if (token != null && jwtProvider.validateToken(token)) {
                        String principalName = jwtProvider.getEmailFromToken(token);
                        accessor.setUser(new UsernamePasswordAuthenticationToken(principalName, null, Collections.emptyList()));
                    }
                }
                return message;
            }
        });
    }

    private HandshakeInterceptor jwtCookieHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler wsHandler,
                    Map<String, Object> attributes
            ) {
                String token = extractTokenFromHandshakeCookie(request);
                if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {
                    attributes.put(WEBSOCKET_USER_EMAIL_ATTRIBUTE, jwtProvider.getEmailFromToken(token));
                }
                return true;
            }

            @Override
            public void afterHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler wsHandler,
                    Exception exception
            ) {
            }
        };
    }

    private DefaultHandshakeHandler jwtCookieHandshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(
                    ServerHttpRequest request,
                    WebSocketHandler wsHandler,
                    Map<String, Object> attributes
            ) {
                String email = (String) attributes.get(WEBSOCKET_USER_EMAIL_ATTRIBUTE);
                if (StringUtils.hasText(email)) {
                    return new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
                }
                return super.determineUser(request, wsHandler, attributes);
            }
        };
    }

    private String extractToken(StompHeaderAccessor accessor) {
        if (accessor.getFirstNativeHeader("Authorization") != null) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            return authHeader;
        }

        String cookieHeader = accessor.getFirstNativeHeader("cookie");
        if (cookieHeader == null) {
            cookieHeader = accessor.getFirstNativeHeader("Cookie");
        }
        if (cookieHeader == null) {
            return null;
        }

        return Arrays.stream(cookieHeader.split(";"))
                .map(String::trim)
                .filter(cookie -> cookie.startsWith(ACCESS_TOKEN_COOKIE_NAME + "="))
                .map(cookie -> cookie.substring((ACCESS_TOKEN_COOKIE_NAME + "=").length()))
                .findFirst()
                .orElse(null);
    }

    private String extractTokenFromHandshakeCookie(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }

        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String[] parseAllowedOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }
}

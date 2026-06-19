package com.jobai.backend.global.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Profile("!local")
public class JwtProvider {

    // application.yaml이나 .env에 등록한 비밀키 (최소 32바이트 이상 문자열 필수)
    @Value("${JWT_SECRET}")
    private String secretKeyString;

    // 만료 시간 설정 (예: 1시간 = 3600000 밀리초)
    private final long accessTokenExpirationPeriod = 3600000L;
    private SecretKey key;

    @PostConstruct
    protected void init() {
        if (secretKeyString == null || secretKeyString.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }
        // 문자열로 된 비밀키를 JJWT에 맞는 SecretKey 객체로 변환
        this.key = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    // 🔑 이메일을 받아 실제 JWT 토큰을 발행하는 메서드
    public String createAccessToken(String email) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenExpirationPeriod);

        return Jwts.builder()
                .subject(email)                 // 토큰 주인 (주로 email이나 ID)
                .issuedAt(now)                  // 토큰 발행 시간
                .expiration(validity)           // 토큰 만료 시간
                .signWith(key)                  // 최신 JJWT는 알고리즘을 자동 선택하거나 암호화 키만 넣어도 작동합니다.
                .compact();
    }

    // JWT 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰에서 이메일(Subject) 추출
    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}

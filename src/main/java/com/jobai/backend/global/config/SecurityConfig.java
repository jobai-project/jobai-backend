package com.jobai.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 기본 로그인 폼 및 HTTP Basic 인증창 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 3. 세션 관리 정책을 STATELESS로 설정
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. URL별 권한 제어 설정
                .authorizeHttpRequests(auth -> auth
                        // 무조건 허용할 경로들
                        .requestMatchers(
                                "/swagger-ui/**",      // Swagger HTML 및 내부 리소스 폴더
                                "/v3/api-docs/**",     // SpringDoc이 생성하는 Open-API JSON 규격 주소
                                "/swagger-ui.html",     // 리다이렉트용 기본 주소
                                "/api/v1/admin/**"  // TODO 테스트를 위해 임시 추가. 추후 조정필요
                        ).permitAll()

                        // 비로그인 상태에서도 접근해야 하는 비즈니스 API 경로 예시 (회원가입/로그인 등)
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // 위에서 명시한 경로 외의 모든 요청은 무조건 인증(로그인)을 거쳐야 함
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
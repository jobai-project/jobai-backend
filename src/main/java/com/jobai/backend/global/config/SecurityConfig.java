package com.jobai.backend.global.config;

import com.jobai.backend.global.auth.CustomOAuth2UserService;
import com.jobai.backend.global.auth.OAuth2SuccessHandler;
import com.jobai.backend.global.auth.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Profile("!local")
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

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
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        // 무조건 허용할 경로들
                        .requestMatchers(
                                "/swagger-ui/**",      // Swagger HTML 및 내부 리소스 폴더
                                "/v3/api-docs/**",     // SpringDoc이 생성하는 Open-API JSON 규격 주소
                                "/swagger-ui.html",     // 리다이렉트용 기본 주소
                                "/api/v1/admin/**",  // 의도된 임시 허용. TODO 테스트를 위해 임시 추가. 추후 조정필요
                                "/login/oauth2/**" // OAuth2 인증 엔드포인트 허용
                        ).permitAll()

                        // 비로그인 상태에서도 접근해야 하는 비즈니스 API 경로 (회원가입/로그인 등)
                        //.requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated() // 위에서 명시한 경로 외의 모든 요청은 무조건 인증(로그인)을 거쳐야 함
                )

                // CORS 설정 연결
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                        // 5. OAuth2 로그인 설정 추가
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                        .userService(customOAuth2UserService)
                                )
                        .successHandler(oAuth2SuccessHandler)
                )
                // 6. JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 2. CORS 상세 설정 Bean 생성
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 오리진 추가
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173",
                "http://api.jobai.site:8080",
                "http://api.jobai.site",
                "https://api.jobai.site",
                "https://jobai.site"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true); // 쿠키 전송 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

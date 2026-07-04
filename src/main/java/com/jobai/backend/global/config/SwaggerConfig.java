package com.jobai.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // 실제 인증은 로그인 성공 시 발급되는 accessToken이 HttpOnly 쿠키로 전달되는 방식이라,
        // Authorization 헤더에 토큰을 붙이는 Bearer 방식이 아니라 쿠키 기반 스킴으로 정의한다.
        String cookieSchemeName = "cookieAuth";

        // 1. 모든 API 요청에 이 보안 요구사항을 기본 적용 (개별 API에서 SecurityRequirement로 재선언)
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(cookieSchemeName);

        // 2. accessToken 쿠키 기반 SecurityScheme 정의 (Swagger UI에서 로그인 상태의 브라우저로 호출 시 자동 적용됨)
        SecurityScheme securityScheme = new SecurityScheme()
                .name("accessToken")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE);

        return new OpenAPI()
                .info(apiInfo()) // API 문서의 기본 메타데이터 정보 주입
                .addSecurityItem(securityRequirement)
                .components(new Components().addSecuritySchemes(cookieSchemeName, securityScheme));
    }

    // API 문서의 제목, 설명, 버전을 설정하는 메드
    private Info apiInfo() {
        return new Info()
                .title("JobAI API 명세서")
                .description("JobAI 백엔드 시스템 인프라 및 핵심 비즈니스 로직 REST API 명세서입니다.")
                .version("1.0.0");
    }
}
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
        // Swagger UI에서 JWT 토큰 인증을 테스트할 수 있도록 보안 스키마 이름을 정의
        String jwtSchemeName = "JWT_Auth";

        // 1. 모든 API 요청 시 자동으로 Authorization 헤더에 JWT가 붙도록 전역 보안 요구사항 설정
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        // 2. JWT Bearer 방식의 SecurityScheme 정의 (Authorization: Bearer <TOKEN> 규격 적용)
        SecurityScheme securityScheme = new SecurityScheme()
                .name(jwtSchemeName)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        return new OpenAPI()
                .info(apiInfo()) // API 문서의 기본 메타데이터 정보 주입
                .addSecurityItem(securityRequirement)
                .components(new Components().addSecuritySchemes(jwtSchemeName, securityScheme));
    }

    // API 문서의 제목, 설명, 버전을 설정하는 메드
    private Info apiInfo() {
        return new Info()
                .title("JobAI API 명세서")
                .description("JobAI 백엔드 시스템 인프라 및 핵심 비즈니스 로직 REST API 명세서입니다.")
                .version("1.0.0");
    }
}
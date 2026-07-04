package com.jobai.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AuthResponse {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        @Schema(description = "이메일 (구글 계정)", example = "user@gmail.com")
        private String email;

        @Schema(description = "이름", example = "홍길동")
        private String name;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginUrl {
        @Schema(description = "구글 소셜 로그인 시작 경로. 프론트엔드에서 이 값으로 리디렉션하면 됨",
                example = "/oauth2/authorization/google")
        private String googleLoginUrl;
    }
}

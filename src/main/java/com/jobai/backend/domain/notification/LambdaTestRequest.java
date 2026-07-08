package com.jobai.backend.domain.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LambdaTestRequest(
        @Schema(description = "알림을 받을 사용자 이름 (필수)", example = "홍길동")
        @NotBlank(message = "사용자 이름은 필수입니다.")
        String userName,

        @Schema(description = "매칭된 회사명 (필수)", example = "카카오")
        @NotBlank(message = "회사 이름은 필수입니다.")
        String companyName,

        @Schema(description = "매칭 점수 (0~100)", example = "85")
        @Min(value = 0, message = "점수는 0점 이상이어야 합니다.")
        @Max(value = 100, message = "점수는 100점 이하이어야 합니다.")
        int score
) {}

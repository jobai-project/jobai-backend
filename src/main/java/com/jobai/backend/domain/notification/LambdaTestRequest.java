package com.jobai.backend.domain.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LambdaTestRequest(
        @NotBlank(message = "사용자 이름은 필수입니다.")
        String userName,

        @NotBlank(message = "회사 이름은 필수입니다.")
        String companyName,

        @Min(value = 0, message = "점수는 0점 이상이어야 합니다.")
        @Max(value = 100, message = "점수는 100점 이하이어야 합니다.")
        int score
) {}
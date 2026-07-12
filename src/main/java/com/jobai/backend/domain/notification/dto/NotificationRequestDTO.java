package com.jobai.backend.domain.notification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class NotificationRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateSettingsDTO {

        @NotNull(message = "이메일 알림 여부는 필수입니다.")
        private Boolean emailEnabled;

        @NotNull(message = "Slack 알림 여부는 필수입니다.")
        private Boolean slackEnabled;

        @NotNull(message = "Discord 알림 여부는 필수입니다.")
        private Boolean discordEnabled;

        @NotNull(message = "적합도 기준은 필수입니다.")
        @Min(value = 0, message = "적합도 기준은 0점 이상이어야 합니다.")
        @Max(value = 100, message = "적합도 기준은 100점 이하이어야 합니다.")
        private Integer matchScoreThreshold;

        @Size(max = 500, message = "Slack webhook URL은 500자 이하로 입력해주세요.")
        private String slackWebhookUrl;

        @Size(max = 500, message = "Discord webhook URL은 500자 이하로 입력해주세요.")
        private String discordWebhookUrl;
    }
}

package com.jobai.backend.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class NotificationResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettingsDTO {

        @Schema(description = "이메일 알림 활성화 여부", example = "true")
        private Boolean emailEnabled;

        @Schema(description = "Slack 알림 활성화 여부", example = "false")
        private Boolean slackEnabled;

        @Schema(description = "Discord 알림 활성화 여부", example = "false")
        private Boolean discordEnabled;

        @Schema(description = "알림 및 추천에 적용할 최소 매칭점수", example = "70", minimum = "0", maximum = "100")
        private Integer matchScoreThreshold;

        @Schema(description = "Slack Incoming Webhook URL", nullable = true)
        private String slackWebhookUrl;

        @Schema(description = "Discord Webhook URL", nullable = true)
        private String discordWebhookUrl;
    }
}
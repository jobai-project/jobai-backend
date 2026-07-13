package com.jobai.backend.domain.notification.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

        @NotNull(message = "Email notification setting is required.")
        private Boolean emailEnabled;

        @NotNull(message = "Slack notification setting is required.")
        private Boolean slackEnabled;

        @NotNull(message = "Discord notification setting is required.")
        private Boolean discordEnabled;

        @NotNull(message = "Match score threshold is required.")
        @Min(value = 0, message = "Match score threshold must be at least 0.")
        @Max(value = 100, message = "Match score threshold must be at most 100.")
        private Integer matchScoreThreshold;

        @Size(max = 500, message = "Slack webhook URL must be 500 characters or less.")
        @Pattern(
                regexp = "^$|^https://hooks\\.slack\\.com/services/.+",
                message = "Slack webhook URL must start with https://hooks.slack.com/services/"
        )
        private String slackWebhookUrl;

        @Size(max = 500, message = "Discord webhook URL must be 500 characters or less.")
        @Pattern(
                regexp = "^$|^https://(discord\\.com|discordapp\\.com)/api/webhooks/.+",
                message = "Discord webhook URL must start with https://discord.com/api/webhooks/"
        )
        private String discordWebhookUrl;

        @AssertTrue(message = "Slack webhook URL is required when Slack notification is enabled.")
        public boolean isSlackWebhookUrlPresentWhenEnabled() {
            return !Boolean.TRUE.equals(slackEnabled) || hasText(slackWebhookUrl);
        }

        @AssertTrue(message = "Discord webhook URL is required when Discord notification is enabled.")
        public boolean isDiscordWebhookUrlPresentWhenEnabled() {
            return !Boolean.TRUE.equals(discordEnabled) || hasText(discordWebhookUrl);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}

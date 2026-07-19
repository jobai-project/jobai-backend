package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.net.URI;
import java.util.LinkedHashMap;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookNotificationService {

    private final WebClient webClient;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    public void sendSlack(String slackUrl, RealtimeNotificationPayload payload) {
        if (!StringUtils.hasText(slackUrl)) {
            return;
        }

        post(slackUrl, Map.of(
                "blocks", List.of(
                        Map.of(
                                "type", "section",
                                "text", Map.of(
                                        "type", "mrkdwn",
                                        "text", buildSlackText(payload)
                                )
                        )
                )
        ), "Slack");
    }

    public void sendDiscord(String discordUrl, RealtimeNotificationPayload payload) {
        if (!StringUtils.hasText(discordUrl)) {
            return;
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", nullToEmpty(payload.title()));
        embed.put("description", nullToEmpty(payload.message()));

        String linkUrl = resolveLinkUrl(payload.linkUrl());
        if (StringUtils.hasText(linkUrl)) {
            embed.put("url", linkUrl);
        }

        post(discordUrl, Map.of("embeds", List.of(embed)), "Discord");
    }

    private void post(String url, Object body, String target) {
        webClient.post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(this::isRetryableWebhookFailure))
                .subscribe(
                        response -> { },
                        error -> log.warn("{} webhook notification failed", target, error)
                );
    }

    private boolean isRetryableWebhookFailure(Throwable error) {
        if (error instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return true;
    }

    private String buildSlackText(RealtimeNotificationPayload payload) {
        String title = nullToEmpty(payload.title());
        String message = nullToEmpty(payload.message());
        String linkUrl = resolveLinkUrl(payload.linkUrl());

        if (StringUtils.hasText(linkUrl)) {
            return "*" + title + "*\n" + message + "\n<" + linkUrl + "|Open>";
        }
        return "*" + title + "*\n" + message;
    }

    private String resolveLinkUrl(String linkUrl) {
        if (!StringUtils.hasText(linkUrl)) {
            return null;
        }

        if (isAbsoluteUrl(linkUrl) || !StringUtils.hasText(frontendBaseUrl)) {
            return linkUrl;
        }

        return frontendBaseUrl.replaceAll("/+$", "") + "/" + linkUrl.replaceAll("^/+", "");
    }

    private boolean isAbsoluteUrl(String linkUrl) {
        try {
            URI uri = URI.create(linkUrl);
            return uri.isAbsolute();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

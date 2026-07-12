package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookNotificationService {

    private final WebClient webClient;

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

        post(discordUrl, Map.of(
                "embeds", List.of(
                        Map.of(
                                "title", nullToEmpty(payload.title()),
                                "description", nullToEmpty(payload.message()),
                                "url", nullToEmpty(payload.linkUrl())
                        )
                )
        ), "Discord");
    }

    private void post(String url, Object body, String target) {
        webClient.post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnError(error -> log.warn("{} webhook notification failed", target, error))
                .subscribe();
    }

    private String buildSlackText(RealtimeNotificationPayload payload) {
        String title = nullToEmpty(payload.title());
        String message = nullToEmpty(payload.message());
        String linkUrl = payload.linkUrl();

        if (StringUtils.hasText(linkUrl)) {
            return "*" + title + "*\n" + message + "\n<" + linkUrl + "|Open>";
        }
        return "*" + title + "*\n" + message;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

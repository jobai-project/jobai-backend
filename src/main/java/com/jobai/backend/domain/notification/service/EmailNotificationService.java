package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final SesV2Client sesV2Client;

    @Value("${notification.email.from:}")
    private String fromEmail;

    @Value("${app.frontend-base-url:}")
    private String frontendBaseUrl;

    public void send(String recipientEmail, RealtimeNotificationPayload payload) {
        if (!StringUtils.hasText(fromEmail)) {
            log.warn("Skip email notification because notification.email.from is not configured");
            return;
        }

        if (!StringUtils.hasText(recipientEmail)) {
            log.warn("Skip email notification because recipient email is blank");
            return;
        }

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromEmail)
                .destination(Destination.builder()
                        .toAddresses(recipientEmail)
                        .build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(content(payload.title()))
                                .body(Body.builder()
                                        .text(content(buildTextBody(payload)))
                                        .html(content(buildHtmlBody(payload)))
                                        .build())
                                .build())
                        .build())
                .build();

        sesV2Client.sendEmail(request);
    }

    private Content content(String value) {
        return Content.builder()
                .charset("UTF-8")
                .data(value)
                .build();
    }

    private String buildTextBody(RealtimeNotificationPayload payload) {
        StringBuilder body = new StringBuilder()
                .append(payload.message());

        String linkUrl = resolveLinkUrl(payload.linkUrl());
        if (StringUtils.hasText(linkUrl)) {
            body.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append("바로가기: ")
                    .append(linkUrl);
        }

        return body.toString();
    }

    private String buildHtmlBody(RealtimeNotificationPayload payload) {
        StringBuilder body = new StringBuilder()
                .append("<p>")
                .append(escapeHtmlWithLineBreaks(payload.message()))
                .append("</p>");

        String linkUrl = resolveLinkUrl(payload.linkUrl());
        if (StringUtils.hasText(linkUrl)) {
            body.append("<p><a href=\"")
                    .append(htmlEscape(linkUrl))
                    .append("\">JobAI에서 확인하기</a></p>");
        }

        return body.toString();
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

    private String escapeHtmlWithLineBreaks(String value) {
        return htmlEscape(value).replace("\n", "<br>");
    }

    private String htmlEscape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }
}

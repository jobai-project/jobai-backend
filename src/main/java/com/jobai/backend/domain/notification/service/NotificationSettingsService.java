package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.notification.dto.NotificationRequestDTO;
import com.jobai.backend.domain.notification.dto.NotificationResponseDTO;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingsService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    public NotificationResponseDTO.SettingsDTO getNotificationSettings(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));

        Notification notification = notificationRepository.findByMemberEmail(email)
                .orElseGet(() -> Notification.createDefault(member));

        return NotificationResponseDTO.SettingsDTO.builder()
                .emailEnabled(notification.getEmailNotification())
                .slackEnabled(notification.getSlackNotification())
                .discordEnabled(notification.getDiscordNotification())
                .matchScoreThreshold(notification.getMatchScoreThreshold())
                .slackWebhookUrl(notification.getSlackWebhookUrl())
                .discordWebhookUrl(notification.getDiscordWebhookUrl())
                .build();
    }

    // 온보딩 4단계: 알림 채널 on/off + 적합도 기준 저장, 온보딩 완료 처리
    @Transactional
    public void updateOnboardingNotificationSettings(String email, NotificationRequestDTO.UpdateSettingsDTO request) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));

        Notification notification = notificationRepository.findByMemberEmail(email)
                .orElseGet(() -> Notification.createDefault(member));

        notification.updateSettings(
                request.getEmailEnabled(),
                request.getSlackEnabled(),
                request.getDiscordEnabled(),
                request.getMatchScoreThreshold(),
                request.getSlackWebhookUrl(),
                request.getDiscordWebhookUrl()
        );
        notificationRepository.save(notification);

        member.completeOnboarding();
    }
}

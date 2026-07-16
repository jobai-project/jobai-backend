package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.notification.dto.NotificationResponseDTO;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationSettingsServiceTest {

    private static final String EMAIL = "test@jobai.com";

    private NotificationRepository notificationRepository;
    private MemberRepository memberRepository;
    private NotificationSettingsService service;
    private Member member;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        memberRepository = mock(MemberRepository.class);
        service = new NotificationSettingsService(notificationRepository, memberRepository);
        member = Member.builder().id(1L).email(EMAIL).build();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
    }

    @Test
    void returnsSavedNotificationSettings() {
        Notification saved = Notification.builder()
                .member(member)
                .emailNotification(false)
                .slackNotification(true)
                .discordNotification(true)
                .matchScoreThreshold(85)
                .slackWebhookUrl("https://hooks.slack.com/services/test")
                .discordWebhookUrl("https://discord.com/api/webhooks/test")
                .build();
        when(notificationRepository.findByMemberEmail(EMAIL)).thenReturn(Optional.of(saved));

        NotificationResponseDTO.SettingsDTO result = service.getNotificationSettings(EMAIL);

        assertThat(result.getEmailEnabled()).isFalse();
        assertThat(result.getSlackEnabled()).isTrue();
        assertThat(result.getDiscordEnabled()).isTrue();
        assertThat(result.getMatchScoreThreshold()).isEqualTo(85);
        assertThat(result.getSlackWebhookUrl()).isEqualTo("https://hooks.slack.com/services/test");
        assertThat(result.getDiscordWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/test");
    }

    @Test
    void returnsDefaultsWhenSettingsHaveNotBeenSaved() {
        when(notificationRepository.findByMemberEmail(EMAIL)).thenReturn(Optional.empty());

        NotificationResponseDTO.SettingsDTO result = service.getNotificationSettings(EMAIL);

        assertThat(result.getEmailEnabled()).isTrue();
        assertThat(result.getSlackEnabled()).isFalse();
        assertThat(result.getDiscordEnabled()).isFalse();
        assertThat(result.getMatchScoreThreshold()).isEqualTo(70);
    }

    @Test
    void throwsWhenMemberDoesNotExist() {
        when(memberRepository.findByEmail("missing@jobai.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotificationSettings("missing@jobai.com"))
                .isInstanceOf(GeneralException.class);
    }
}
package com.jobai.backend.domain.member.service;

import com.jobai.backend.domain.application.repository.ApplicationRepository;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.event.MemberWithdrawalCompletedEvent;
import com.jobai.backend.domain.member.repository.MatchScoreRepository;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.member.repository.RefreshTokenRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.repository.NotificationMatchBatchRepository;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.scrap.repository.MemberScrapHistoryRepository;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalServiceTest {

    @InjectMocks
    private MemberWithdrawalService memberWithdrawalService;

    @Mock private MemberRepository memberRepository;
    @Mock private ResumesRepository resumesRepository;
    @Mock private PrivateMatchScoreRepository privateMatchScoreRepository;
    @Mock private PublicMatchScoreRepository publicMatchScoreRepository;
    @Mock private MatchScoreRepository matchScoreRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationMatchBatchRepository notificationMatchBatchRepository;
    @Mock private MemberScrapHistoryRepository memberScrapHistoryRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void withdrawDeletesMemberDataAndPublishesFileCleanupEvent() {
        Member member = Member.builder().id(1L).email("member@example.com").build();
        Resumes firstResume = Resumes.builder().id(10L).storedFileUrl("https://bucket/first.pdf").build();
        Resumes secondResume = Resumes.builder().id(11L).storedFileUrl("https://bucket/second.pdf").build();
        when(memberRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
        when(resumesRepository.findByMemberId(1L)).thenReturn(List.of(firstResume, secondResume));
        when(notificationMatchBatchRepository.findByMemberId(1L)).thenReturn(List.of());

        memberWithdrawalService.withdraw("member@example.com");

        InOrder inOrder = inOrder(
                privateMatchScoreRepository, publicMatchScoreRepository, resumesRepository, memberRepository
        );
        inOrder.verify(privateMatchScoreRepository).deleteByResumeId(10L);
        inOrder.verify(publicMatchScoreRepository).deleteByResumeId(10L);
        inOrder.verify(privateMatchScoreRepository).deleteByResumeId(11L);
        inOrder.verify(publicMatchScoreRepository).deleteByResumeId(11L);
        inOrder.verify(resumesRepository).deleteAll(List.of(firstResume, secondResume));
        inOrder.verify(memberRepository).delete(member);
        inOrder.verify(memberRepository).flush();

        verify(matchScoreRepository).deleteByMemberId(1L);
        verify(applicationRepository).deleteByMemberId(1L);
        verify(notificationRepository).deleteByMemberId(1L);
        verify(notificationMatchBatchRepository).deleteAll(List.of());
        verify(memberScrapHistoryRepository).deleteByMemberId(1L);
        verify(refreshTokenRepository).deleteByMemberId(1L);

        ArgumentCaptor<MemberWithdrawalCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(MemberWithdrawalCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().resumeFileUrls())
                .containsExactly("https://bucket/first.pdf", "https://bucket/second.pdf");
    }

    @Test
    void withdrawThrowsWhenMemberDoesNotExist() {
        when(memberRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberWithdrawalService.withdraw("missing@example.com"))
                .isInstanceOf(GeneralException.class);

        verify(resumesRepository, never()).findByMemberId(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}

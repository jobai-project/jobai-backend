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
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberWithdrawalService {

    private final MemberRepository memberRepository;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;
    private final MatchScoreRepository matchScoreRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationMatchBatchRepository notificationMatchBatchRepository;
    private final MemberScrapHistoryRepository memberScrapHistoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void withdraw(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND));

        Long memberId = member.getId();
        List<Resumes> resumes = resumesRepository.findByMemberId(memberId);
        List<String> resumeFileUrls = resumes.stream()
                .map(Resumes::getStoredFileUrl)
                .filter(fileUrl -> fileUrl != null && !fileUrl.isBlank())
                .toList();

        for (Resumes resume : resumes) {
            privateMatchScoreRepository.deleteByResumeId(resume.getId());
            publicMatchScoreRepository.deleteByResumeId(resume.getId());
        }

        matchScoreRepository.deleteByMemberId(memberId);
        notificationMatchBatchRepository.deleteAll(notificationMatchBatchRepository.findByMemberId(memberId));
        applicationRepository.deleteByMemberId(memberId);
        notificationRepository.deleteByMemberId(memberId);
        memberScrapHistoryRepository.deleteByMemberId(memberId);
        refreshTokenRepository.deleteByMemberId(memberId);
        resumesRepository.deleteAll(resumes);
        memberRepository.delete(member);
        memberRepository.flush();

        eventPublisher.publishEvent(new MemberWithdrawalCompletedEvent(resumeFileUrls));
    }
}

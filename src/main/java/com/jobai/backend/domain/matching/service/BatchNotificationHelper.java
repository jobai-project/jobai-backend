package com.jobai.backend.domain.matching.service;

import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.entity.PublicMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.notification.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 배치 점수 산출 후 임계값 이상 공고에 대한 알림 발송을 담당하는 공통 헬퍼.
 *
 * <p>Private/Public 배치 서비스에서 동일하게 사용하여 메시지 포맷팅과 예외 처리 정책의 중복을 방지한다.
 * 알림 발송 실패 시에도 점수 산출에는 영향을 주지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchNotificationHelper {

    private final NotificationDispatchService notificationDispatchService;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;
    private final NotificationRepository notificationRepository;

    /** 임계값 이상 점수를 받은 공고 정보. 알림 메시지 구성에 사용된다. */
    public record ScoredPosting(String title, String company, int score, Long postingId, String linkPrefix) {
    }

    /**
     * 임계값 이상 공고가 있으면 사용자에게 묶음 알림을 발송한다.
     * 여러 건이면 모든 공고의 회사명, 제목, 점수를 점수 내림차순으로 표시한다.
     *
     * @param member 알림 수신 대상 회원
     * @param aboveThreshold 임계값 이상 공고 목록
     * @param notificationType 알림 제목 (예: "새 추천 공고", "새 추천 공고 (공기업)")
     */
    public void sendIfNeeded(Member member, List<ScoredPosting> aboveThreshold, String notificationType) {
        if (aboveThreshold.isEmpty()) return;

        try {
            List<ScoredPosting> sortedPostings = aboveThreshold.stream()
                    .sorted(Comparator.comparingInt(ScoredPosting::score).reversed())
                    .toList();

            String message = buildNotificationMessage(sortedPostings);
            String linkUrl = sortedPostings.size() == 1
                    ? sortedPostings.get(0).linkPrefix() + sortedPostings.get(0).postingId()
                    : "/jobs";

            notificationDispatchService.notifyUser(
                    member.getEmail(),
                    RealtimeNotificationPayload.of("MATCH", notificationType, message, linkUrl)
            );
            log.info("[배치알림] {} — {}건 알림 발송", member.getEmail(), aboveThreshold.size());
        } catch (Exception e) {
            log.warn("[배치알림] 알림 발송 실패: email={}, error={}", member.getEmail(), e.getMessage());
        }
    }

    private String buildNotificationMessage(List<ScoredPosting> postings) {
        return postings.stream()
                .map(posting -> String.format("[%s] %s (%d점)", posting.company(), posting.title(), posting.score()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /** 전체 대상으로 알림 발송. */
    public int sendNotificationsForExistingScores() {
        return sendNotificationsForExistingScores(null);
    }

    /**
     * 기존 DB에 저장된 점수를 기반으로 임계값 이상 공고에 대해 알림을 발송한다.
     * 점수 산출은 하지 않으며, 알림 파이프라인만 테스트한다.
     *
     * @param targetEmail 특정 사용자에게만 발송할 이메일. null이면 전체 대상.
     * @return 알림 발송된 공고 건수
     */
    public int sendNotificationsForExistingScores(String targetEmail) {
        List<Resumes> resumes = resumesRepository.findAllActiveWithEmbedding();
        if (resumes.isEmpty()) {
            return -1;
        }

        int totalNotified = 0;
        for (Resumes resume : resumes) {
            Member member = resume.getMember();
            if (!member.isOnboardingCompleted()) continue;
            if (targetEmail != null && !targetEmail.equals(member.getEmail())) continue;

            String email = member.getEmail();
            int threshold = notificationRepository.findByMemberEmail(email)
                    .map(Notification::getMatchScoreThreshold)
                    .orElse(70);

            List<ScoredPosting> aboveThreshold = new ArrayList<>();
            for (PrivateMatchScore s : privateMatchScoreRepository.findByResumeId(resume.getId())) {
                if (s.getScore() >= threshold) {
                    aboveThreshold.add(new ScoredPosting(
                            s.getPrivateJobPosting().getTitle(),
                            s.getPrivateJobPosting().getCompany(),
                            s.getScore(), s.getPrivateJobPosting().getId(), "/jobs/private/"));
                }
            }
            if (!aboveThreshold.isEmpty()) {
                sendIfNeeded(resume.getMember(), aboveThreshold, "새 추천 공고");
                totalNotified += aboveThreshold.size();
            }

            List<ScoredPosting> publicAbove = new ArrayList<>();
            for (PublicMatchScore s : publicMatchScoreRepository.findByResumeId(resume.getId())) {
                if (s.getScore() >= threshold) {
                    publicAbove.add(new ScoredPosting(
                            s.getPublicJobPosting().getTitle(),
                            s.getPublicJobPosting().getCompanyName(),
                            s.getScore(), s.getPublicJobPosting().getId(), "/jobs/public/"));
                }
            }
            if (!publicAbove.isEmpty()) {
                sendIfNeeded(resume.getMember(), publicAbove, "새 추천 공고 (공기업)");
                totalNotified += publicAbove.size();
            }
        }
        return totalNotified;
    }
}

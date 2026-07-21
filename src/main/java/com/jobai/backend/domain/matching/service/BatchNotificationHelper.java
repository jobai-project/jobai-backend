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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 배치 점수 산출 후 임계값 이상 공고에 대한 알림 발송을 담당하는 공통 헬퍼.
 *
 * <p>Private/Public 배치 서비스에서 동일하게 사용하여 메시지 포맷팅·예외 처리 정책의 중복을 방지한다.
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

    /** 한 사용자에게 발송할 알림 대상 공고 묶음. 사기업/공기업 구분 없이 통합하여 사용한다. */
    public record MemberNotifications(Member member, List<ScoredPosting> postings) {
    }

    /** 배치 점수 산출 결과. 요약 문자열과 사용자별 알림 데이터를 포함한다. */
    public record BatchScoringResult(String summary, Map<String, MemberNotifications> notifications) {
    }

    /**
     * 임계값 이상 공고가 있으면 사용자에게 묶음 알림을 발송한다.
     * 1건이면 해당 공고 제목·점수를 표시하고, 여러 건이면 최고 점수 공고 + 나머지 건수로 요약한다.
     *
     * @param member         알림 수신 대상 회원
     * @param aboveThreshold 임계값 이상 공고 목록
     * @param notificationType 알림 제목 (예: "새 추천 공고", "새 추천 공고 (공기업)")
     */
    public void sendIfNeeded(Member member, List<ScoredPosting> aboveThreshold, String notificationType) {
        if (aboveThreshold.isEmpty()) return;

        try {
            String message;
            String linkUrl;
            if (aboveThreshold.size() == 1) {
                ScoredPosting top = aboveThreshold.get(0);
                message = String.format("[%s] %s (매칭 %d점)", top.company(), top.title(), top.score());
                linkUrl = top.linkPrefix() + top.postingId();
            } else {
                ScoredPosting top = aboveThreshold.stream()
                        .max(Comparator.comparingInt(ScoredPosting::score))
                        .orElse(aboveThreshold.get(0));
                message = String.format("[%s] %s 외 %d건 (최고 %d점)",
                        top.company(), top.title(), aboveThreshold.size() - 1, top.score());
                linkUrl = "/jobs";
            }

            notificationDispatchService.notifyUser(
                    member.getEmail(),
                    RealtimeNotificationPayload.of("MATCH", notificationType, message, linkUrl)
            );
            log.info("[배치알림] {} — {}건 알림 발송", member.getEmail(), aboveThreshold.size());
        } catch (Exception e) {
            log.warn("[배치알림] 알림 발송 실패: email={}, error={}", member.getEmail(), e.getMessage());
        }
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

        // 루프 진입 전 기준 시간을 한 번만 계산한다.
        LocalDateTime recentThreshold = LocalDateTime.now().minusHours(24);

        int totalNotified = 0;
        for (Resumes resume : resumes) {
            Member member = resume.getMember();
            if (!member.isOnboardingCompleted()) continue;
            if (targetEmail != null && !targetEmail.equals(member.getEmail())) continue;

            String email = member.getEmail();
            int threshold = notificationRepository.findByMemberEmail(email)
                    .map(Notification::getMatchScoreThreshold)
                    .orElse(70);

            // 사기업·공기업 결과를 하나의 리스트로 합쳐 알림 1건으로 발송한다.
            List<ScoredPosting> allAbove = new ArrayList<>();
            for (PrivateMatchScore s : privateMatchScoreRepository.findByResumeId(resume.getId())) {
                if (s.getScore() >= threshold
                        && s.getPrivateJobPosting().getCreatedAt() != null
                        && s.getPrivateJobPosting().getCreatedAt().isAfter(recentThreshold)) {
                    allAbove.add(new ScoredPosting(
                            s.getPrivateJobPosting().getTitle(),
                            s.getPrivateJobPosting().getCompany(),
                            s.getScore(), s.getPrivateJobPosting().getId(), "/jobs/private/"));
                }
            }
            for (PublicMatchScore s : publicMatchScoreRepository.findByResumeId(resume.getId())) {
                if (s.getScore() >= threshold
                        && s.getPublicJobPosting().getCreatedAt() != null
                        && s.getPublicJobPosting().getCreatedAt().isAfter(recentThreshold)) {
                    allAbove.add(new ScoredPosting(
                            s.getPublicJobPosting().getTitle(),
                            s.getPublicJobPosting().getCompanyName(),
                            s.getScore(), s.getPublicJobPosting().getId(), "/jobs/public/"));
                }
            }

            if (!allAbove.isEmpty()) {
                sendIfNeeded(resume.getMember(), allAbove, "새 추천 공고");
                totalNotified += allAbove.size();
            }
        }
        return totalNotified;
    }
}

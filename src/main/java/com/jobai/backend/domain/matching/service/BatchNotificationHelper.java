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
import com.jobai.backend.domain.notification.service.NotificationMatchBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchNotificationHelper {

    private static final String MATCH_NOTIFICATION_LINK_PREFIX = "/notifications/matches/";

    private final NotificationDispatchService notificationDispatchService;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationMatchBatchService notificationMatchBatchService;

    public record ScoredPosting(
            String source,
            String title,
            String company,
            int score,
            Long postingId,
            String linkPrefix,
            String location,
            String employmentType,
            String jobCategory,
            LocalDate deadline
    ) {
    }

    public record MemberNotifications(Member member, List<ScoredPosting> postings) {
    }

    public record BatchScoringResult(String summary, Map<String, MemberNotifications> notifications) {
    }

    public void sendIfNeeded(Member member, List<ScoredPosting> aboveThreshold, String notificationType) {
        if (aboveThreshold.isEmpty()) return;

        try {
            List<ScoredPosting> sortedPostings = aboveThreshold.stream()
                    .sorted(Comparator.comparingInt(ScoredPosting::score).reversed())
                    .toList();

            Long batchId = notificationMatchBatchService.create(
                    member,
                    notificationType,
                    sortedPostings.stream()
                            .map(this::toBatchItemCommand)
                            .toList()
            );

            ScoredPosting top = sortedPostings.get(0);
            String message = sortedPostings.size() == 1
                    ? String.format("[%s] %s (매칭 %d점)", top.company(), top.title(), top.score())
                    : String.format("[%s] %s 외 %d건 (최고 %d점)",
                            top.company(), top.title(), sortedPostings.size() - 1, top.score());
            String linkUrl = MATCH_NOTIFICATION_LINK_PREFIX + batchId;

            notificationDispatchService.notifyUser(
                    member.getEmail(),
                    RealtimeNotificationPayload.of("MATCH", notificationType, message, linkUrl)
            );
            log.info("[배치알림] {} — {}건 알림 발송", member.getEmail(), aboveThreshold.size());
        } catch (Exception e) {
            log.warn("[배치알림] 알림 발송 실패: email={}, error={}", member.getEmail(), e.getMessage());
        }
    }

    private NotificationMatchBatchService.BatchItemCommand toBatchItemCommand(ScoredPosting posting) {
        return new NotificationMatchBatchService.BatchItemCommand(
                posting.source(),
                posting.postingId(),
                posting.title(),
                posting.company(),
                posting.location(),
                posting.employmentType(),
                posting.jobCategory(),
                posting.deadline(),
                posting.score(),
                posting.linkPrefix() + posting.postingId()
        );
    }

    public int sendNotificationsForExistingScores() {
        return sendNotificationsForExistingScores(null);
    }

    public int sendNotificationsForExistingScores(String targetEmail) {
        List<Resumes> resumes = resumesRepository.findAllActiveWithEmbedding();
        if (resumes.isEmpty()) {
            return -1;
        }

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

            List<ScoredPosting> allAbove = new ArrayList<>();
            for (PrivateMatchScore score : privateMatchScoreRepository.findByResumeId(resume.getId())) {
                if (score.getScore() >= threshold
                        && score.getPrivateJobPosting().getCreatedAt() != null
                        && score.getPrivateJobPosting().getCreatedAt().isAfter(recentThreshold)) {
                    allAbove.add(new ScoredPosting(
                            "PRIVATE",
                            score.getPrivateJobPosting().getTitle(),
                            score.getPrivateJobPosting().getCompany(),
                            score.getScore(),
                            score.getPrivateJobPosting().getId(),
                            "/jobs/private/",
                            score.getPrivateJobPosting().getLocation(),
                            score.getPrivateJobPosting().getEmploymentType(),
                            score.getPrivateJobPosting().getJobCategory(),
                            score.getPrivateJobPosting().getDeadline()
                    ));
                }
            }

            for (PublicMatchScore score : publicMatchScoreRepository.findByResumeId(resume.getId())) {
                if (score.getScore() >= threshold
                        && score.getPublicJobPosting().getCreatedAt() != null
                        && score.getPublicJobPosting().getCreatedAt().isAfter(recentThreshold)) {
                    allAbove.add(new ScoredPosting(
                            "PUBLIC",
                            score.getPublicJobPosting().getTitle(),
                            score.getPublicJobPosting().getCompanyName(),
                            score.getScore(),
                            score.getPublicJobPosting().getId(),
                            "/jobs/public/",
                            score.getPublicJobPosting().getWorkRegion(),
                            score.getPublicJobPosting().getRecrutType(),
                            null,
                            score.getPublicJobPosting().getEndDate()
                    ));
                }
            }

            if (!allAbove.isEmpty()) {
                sendIfNeeded(member, allAbove, "새 추천 공고");
                totalNotified += allAbove.size();
            }
        }
        return totalNotified;
    }
}

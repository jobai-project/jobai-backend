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
import com.jobai.backend.global.kafka.event.NotificationDispatchEvent;
import com.jobai.backend.global.kafka.producer.KafkaNotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BatchNotificationHelper {

    private static final String MATCH_NOTIFICATION_LINK_PREFIX = "/notifications/matches/";

    private final NotificationDispatchService notificationDispatchService;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationMatchBatchService notificationMatchBatchService;
    private final ObjectProvider<KafkaNotificationProducer> kafkaNotificationProducer;
    private final boolean kafkaNotificationEnabled;

    public BatchNotificationHelper(
            NotificationDispatchService notificationDispatchService,
            ResumesRepository resumesRepository,
            PrivateMatchScoreRepository privateMatchScoreRepository,
            PublicMatchScoreRepository publicMatchScoreRepository,
            NotificationRepository notificationRepository,
            NotificationMatchBatchService notificationMatchBatchService,
            ObjectProvider<KafkaNotificationProducer> kafkaNotificationProducer,
            @Value("${kafka.notification.enabled:false}") boolean kafkaNotificationEnabled
    ) {
        this.notificationDispatchService = notificationDispatchService;
        this.resumesRepository = resumesRepository;
        this.privateMatchScoreRepository = privateMatchScoreRepository;
        this.publicMatchScoreRepository = publicMatchScoreRepository;
        this.notificationRepository = notificationRepository;
        this.notificationMatchBatchService = notificationMatchBatchService;
        this.kafkaNotificationProducer = kafkaNotificationProducer;
        this.kafkaNotificationEnabled = kafkaNotificationEnabled;
    }

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

    /**
     * 임계값 이상 공고가 있으면 알림을 발송한다.
     * kafka.notification.enabled=true이면 Kafka로 발행, 아니면 직접 발송.
     */
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

            RealtimeNotificationPayload payload =
                    RealtimeNotificationPayload.of("MATCH", notificationType, message, linkUrl);

            KafkaNotificationProducer producer = kafkaNotificationProducer.getIfAvailable();
            if (kafkaNotificationEnabled && producer != null) {
                producer.send(new NotificationDispatchEvent(
                        member.getEmail(),
                        payload.type(),
                        payload.title(),
                        payload.message(),
                        payload.linkUrl(),
                        payload.createdAt()
                ));
                log.info("[배치알림] {} — {}건 Kafka 발행", maskEmail(member.getEmail()), aboveThreshold.size());
            } else {
                notificationDispatchService.notifyUser(member.getEmail(), payload);
                log.info("[배치알림] {} — {}건 직접 발송", maskEmail(member.getEmail()), aboveThreshold.size());
            }
        } catch (Exception e) {
            log.warn("[배치알림] 알림 발송 실패: email={}, error={}", maskEmail(member.getEmail()), e.getMessage());
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

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }

    /** 기존 점수 기반으로 모든 활성 이력서에 대해 알림을 발송한다. */
    public int sendNotificationsForExistingScores() {
        return sendNotificationsForExistingScores(null);
    }

    /**
     * 기존 점수 기반으로 알림을 발송한다. targetEmail이 지정되면 해당 사용자만 처리.
     *
     * @return 알림 발송된 공고 수. 활성 이력서가 없으면 -1.
     */
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

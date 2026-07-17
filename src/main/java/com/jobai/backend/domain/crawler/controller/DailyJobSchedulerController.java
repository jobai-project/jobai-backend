package com.jobai.backend.domain.crawler.controller;

import com.jobai.backend.domain.crawler.scheduler.DailyJobScheduler;
import com.jobai.backend.domain.crawler.service.PrivateJobPostingService;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.entity.PublicMatchScore;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.home.service.BatchNotificationHelper;
import com.jobai.backend.domain.home.service.PrivateMatchBatchService;
import com.jobai.backend.domain.home.service.PublicMatchBatchService;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import com.jobai.backend.domain.techcard.service.TechCardCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 새벽 파이프라인 수동 트리거용 API.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
public class DailyJobSchedulerController implements DailyJobSchedulerControllerDocs {

    private final DailyJobScheduler dailyJobScheduler;
    private final PrivateJobPostingService privateJobPostingService;
    private final EmbeddingBatchService embeddingBatchService;
    private final PrivateMatchBatchService privateMatchBatchService;
    private final PublicMatchBatchService publicMatchBatchService;
    private final TechCardCollectService techCardCollectService;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;
    private final NotificationRepository notificationRepository;
    private final BatchNotificationHelper batchNotificationHelper;

    @Override
    @PostMapping("/daily-pipeline")
    public ResponseEntity<String> triggerDailyPipeline() {
        dailyJobScheduler.runDailyPipeline();
        return ResponseEntity.ok("새벽 파이프라인 실행 완료");
    }

    @Override
    @PostMapping("/classify")
    public ResponseEntity<String> classifyUnclassified() {
        int total = privateJobPostingService.classifyUnclassified(100);
        return ResponseEntity.ok("미분류 공고 " + total + "건 분류 완료");
    }

    @Override
    @PostMapping("/classify-employment")
    public ResponseEntity<String> classifyMissingEmploymentTypes() {
        int total = privateJobPostingService.classifyMissingEmploymentTypes(100);
        return ResponseEntity.ok("고용형태/경력 미분류 공고 " + total + "건 분류 완료");
    }

    @Override
    @PostMapping("/classify-region")
    public ResponseEntity<String> classifyMissingRegions() {
        int total = privateJobPostingService.classifyMissingRegions(100);
        return ResponseEntity.ok("지역 미분류 공고 " + total + "건 분류 완료");
    }

    @Override
    @PostMapping("/embedding")
    public ResponseEntity<String> generateEmbeddings() {
        embeddingBatchService.generateMissingEmbeddings();
        return ResponseEntity.ok("임베딩 생성 완료");
    }

    @Override
    @PostMapping("/scoring")
    public ResponseEntity<String> scorePostings() {
        privateMatchBatchService.scoreNewAndUpdatedPostings();
        return ResponseEntity.ok("매칭 점수 산출 완료");
    }

    @Override
    @PostMapping("/scoring-public")
    public ResponseEntity<String> scorePublicPostings() {
        publicMatchBatchService.scoreNewAndUpdatedPostings();
        return ResponseEntity.ok("공기업 매칭 점수 산출 완료");
    }

    @Override
    @PostMapping("/resume-embedding")
    public ResponseEntity<String> generateResumeEmbeddings() {
        String result = embeddingBatchService.generateMissingResumeEmbeddings();
        return ResponseEntity.ok(result);
    }

    @Override
    @PostMapping("/tech-cards")
    public ResponseEntity<String> collectTechCards() {
        techCardCollectService.collectAndSummarize();
        return ResponseEntity.ok("IT 뉴스 카드 수집 완료");
    }

    @Override
    @PostMapping("/notify-test")
    public ResponseEntity<String> triggerNotifyTest() {
        List<Resumes> resumes = resumesRepository.findAllActiveWithEmbedding();
        if (resumes.isEmpty()) {
            return ResponseEntity.ok("활성 이력서가 없어 알림 테스트 불가");
        }

        int totalNotified = 0;
        for (Resumes resume : resumes) {
            String email = resume.getMember().getEmail();
            int threshold = notificationRepository.findByMemberEmail(email)
                    .map(Notification::getMatchScoreThreshold)
                    .orElse(70);

            List<BatchNotificationHelper.ScoredPosting> aboveThreshold = new ArrayList<>();

            // 사기업 점수 조회
            for (PrivateMatchScore s : privateMatchScoreRepository.findByResumeId(resume.getId())) {
                if (s.getScore() >= threshold) {
                    aboveThreshold.add(new BatchNotificationHelper.ScoredPosting(
                            s.getPrivateJobPosting().getTitle(),
                            s.getPrivateJobPosting().getCompany(),
                            s.getScore(), s.getPrivateJobPosting().getId(), "/jobs/private/"));
                }
            }
            if (!aboveThreshold.isEmpty()) {
                batchNotificationHelper.sendIfNeeded(resume.getMember(), aboveThreshold, "새 추천 공고");
                totalNotified += aboveThreshold.size();
            }

            // 공기업 점수 조회
            List<BatchNotificationHelper.ScoredPosting> publicAbove = new ArrayList<>();
            for (PublicMatchScore s : publicMatchScoreRepository.findByResumeId(resume.getId())) {
                if (s.getScore() >= threshold) {
                    publicAbove.add(new BatchNotificationHelper.ScoredPosting(
                            s.getPublicJobPosting().getTitle(),
                            s.getPublicJobPosting().getCompanyName(),
                            s.getScore(), s.getPublicJobPosting().getId(), "/jobs/public/"));
                }
            }
            if (!publicAbove.isEmpty()) {
                batchNotificationHelper.sendIfNeeded(resume.getMember(), publicAbove, "새 추천 공고 (공기업)");
                totalNotified += publicAbove.size();
            }
        }

        return ResponseEntity.ok("알림 테스트 완료 — 임계값 이상 공고 " + totalNotified + "건 알림 발송");
    }
}

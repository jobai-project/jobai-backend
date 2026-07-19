package com.jobai.backend.domain.privatejobposting.controller;

import com.jobai.backend.domain.privatejobposting.scheduler.DailyJobScheduler;
import com.jobai.backend.domain.privatejobposting.service.PrivateJobPostingService;
import com.jobai.backend.domain.matching.service.BatchNotificationHelper;
import com.jobai.backend.domain.matching.service.PrivateMatchBatchService;
import com.jobai.backend.domain.matching.service.PublicMatchBatchService;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import com.jobai.backend.domain.techcard.service.TechCardCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 새벽 파이프라인 수동 트리거용 API.
 *
 * <p>장시간 작업(임베딩, 점수 산출 등)은 {@code schedulerTaskExecutor}로 백그라운드 실행하고
 * 즉시 202 Accepted를 반환한다. 각 비동기 작업의 진행 상태는 {@code GET /status}로 조회 가능.</p>
 *
 * <p>상태값: RUNNING(실행 중) → COMPLETED(정상 완료) / FAILED(예외 발생).
 * in-memory 저장이므로 서버 재시작 시 초기화된다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scheduler")
public class DailyJobSchedulerController implements DailyJobSchedulerControllerDocs {

    private final DailyJobScheduler dailyJobScheduler;
    private final PrivateJobPostingService privateJobPostingService;
    private final EmbeddingBatchService embeddingBatchService;
    private final PrivateMatchBatchService privateMatchBatchService;
    private final PublicMatchBatchService publicMatchBatchService;
    private final TechCardCollectService techCardCollectService;
    private final BatchNotificationHelper batchNotificationHelper;
    private final Executor schedulerTaskExecutor;

    /** 비동기 작업 상태 추적용. key: 작업명, value: {status, result} */
    private final Map<String, Map<String, String>> taskStatus = new ConcurrentHashMap<>();

    public DailyJobSchedulerController(
            DailyJobScheduler dailyJobScheduler,
            PrivateJobPostingService privateJobPostingService,
            EmbeddingBatchService embeddingBatchService,
            PrivateMatchBatchService privateMatchBatchService,
            PublicMatchBatchService publicMatchBatchService,
            TechCardCollectService techCardCollectService,
            BatchNotificationHelper batchNotificationHelper,
            @Qualifier("schedulerTaskExecutor") Executor schedulerTaskExecutor
    ) {
        this.dailyJobScheduler = dailyJobScheduler;
        this.privateJobPostingService = privateJobPostingService;
        this.embeddingBatchService = embeddingBatchService;
        this.privateMatchBatchService = privateMatchBatchService;
        this.publicMatchBatchService = publicMatchBatchService;
        this.techCardCollectService = techCardCollectService;
        this.batchNotificationHelper = batchNotificationHelper;
        this.schedulerTaskExecutor = schedulerTaskExecutor;
    }

    @Override
    @PostMapping("/daily-pipeline")
    public ResponseEntity<String> triggerDailyPipeline() {
        taskStatus.put("daily-pipeline", Map.of("status", "RUNNING"));
        schedulerTaskExecutor.execute(() -> {
            try {
                String result = dailyJobScheduler.runDailyPipeline();
                taskStatus.put("daily-pipeline", Map.of("status", "COMPLETED", "result", result));
            } catch (Exception e) {
                taskStatus.put("daily-pipeline", Map.of("status", "FAILED", "result", e.getMessage()));
                log.error("[수동트리거] 새벽 파이프라인 실행 실패: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body("새벽 파이프라인 실행 시작됨 (백그라운드)");
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
    @PostMapping("/classify-location")
    public ResponseEntity<String> classifyMissingRegions() {
        int total = privateJobPostingService.classifyMissingRegions(100);
        return ResponseEntity.ok("지역 미분류 공고 " + total + "건 분류 완료");
    }

    @Override
    @PostMapping("/embedding")
    public ResponseEntity<String> generateEmbeddings() {
        taskStatus.put("embedding", Map.of("status", "RUNNING"));
        schedulerTaskExecutor.execute(() -> {
            try {
                embeddingBatchService.generateAllMissingEmbeddings();
                taskStatus.put("embedding", Map.of("status", "COMPLETED"));
            } catch (Exception e) {
                taskStatus.put("embedding", Map.of("status", "FAILED", "result", e.getMessage()));
                log.error("[수동트리거] 임베딩 생성 실패: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body("공고 임베딩 생성 시작됨 (백그라운드)");
    }

    @Override
    @PostMapping("/scoring")
    public ResponseEntity<String> scorePostings() {
        taskStatus.put("scoring", Map.of("status", "RUNNING"));
        schedulerTaskExecutor.execute(() -> {
            try {
                privateMatchBatchService.scoreNewAndUpdatedPostings();
                taskStatus.put("scoring", Map.of("status", "COMPLETED"));
            } catch (Exception e) {
                taskStatus.put("scoring", Map.of("status", "FAILED", "result", e.getMessage()));
                log.error("[수동트리거] 사기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body("사기업 매칭 점수 산출 시작됨 (백그라운드)");
    }

    @Override
    @PostMapping("/scoring-public")
    public ResponseEntity<String> scorePublicPostings() {
        taskStatus.put("scoring-public", Map.of("status", "RUNNING"));
        schedulerTaskExecutor.execute(() -> {
            try {
                publicMatchBatchService.scoreNewAndUpdatedPostings();
                taskStatus.put("scoring-public", Map.of("status", "COMPLETED"));
            } catch (Exception e) {
                taskStatus.put("scoring-public", Map.of("status", "FAILED", "result", e.getMessage()));
                log.error("[수동트리거] 공기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body("공기업 매칭 점수 산출 시작됨 (백그라운드)");
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
        taskStatus.put("tech-cards", Map.of("status", "RUNNING"));
        schedulerTaskExecutor.execute(() -> {
            try {
                techCardCollectService.collectAndSummarize();
                taskStatus.put("tech-cards", Map.of("status", "COMPLETED"));
            } catch (Exception e) {
                taskStatus.put("tech-cards", Map.of("status", "FAILED", "result", e.getMessage()));
                log.error("[수동트리거] IT 뉴스 카드 수집 실패: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body("IT 뉴스 카드 수집 시작됨 (백그라운드)");
    }

    @Override
    @PostMapping("/notify-test")
    public ResponseEntity<String> triggerNotifyTest(
            @RequestParam(required = false) String email
    ) {
        int result = batchNotificationHelper.sendNotificationsForExistingScores(email);
        if (result < 0) {
            return ResponseEntity.ok("활성 이력서가 없어 알림 테스트 불가");
        }
        return ResponseEntity.ok("알림 테스트 완료 — 임계값 이상 공고 " + result + "건 알림 발송");
    }

    @Override
    @GetMapping("/status")
    public ResponseEntity<Map<String, Map<String, String>>> getTaskStatus() {
        return ResponseEntity.ok(taskStatus);
    }
}

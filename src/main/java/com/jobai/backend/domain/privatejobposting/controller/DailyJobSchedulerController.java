package com.jobai.backend.domain.privatejobposting.controller;

import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.privatejobposting.scheduler.DailyJobScheduler;
import com.jobai.backend.domain.privatejobposting.service.PrivateJobPostingService;
import com.jobai.backend.domain.matching.service.BatchNotificationHelper;
import com.jobai.backend.domain.matching.service.PrivateMatchBatchService;
import com.jobai.backend.domain.matching.service.PublicMatchBatchService;
import com.jobai.backend.domain.matching.service.ScoringDispatcher;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import com.jobai.backend.domain.techcard.service.TechCardCollectService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
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
    private final ObjectProvider<ScoringDispatcher> scoringDispatcher;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
    private final Environment environment;

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
            @Qualifier("schedulerTaskExecutor") Executor schedulerTaskExecutor,
            ObjectProvider<ScoringDispatcher> scoringDispatcher,
            PrivateMatchScoreRepository privateMatchScoreRepository,
            ObjectProvider<StringRedisTemplate> stringRedisTemplate,
            Environment environment
    ) {
        this.dailyJobScheduler = dailyJobScheduler;
        this.privateJobPostingService = privateJobPostingService;
        this.embeddingBatchService = embeddingBatchService;
        this.privateMatchBatchService = privateMatchBatchService;
        this.publicMatchBatchService = publicMatchBatchService;
        this.techCardCollectService = techCardCollectService;
        this.batchNotificationHelper = batchNotificationHelper;
        this.schedulerTaskExecutor = schedulerTaskExecutor;
        this.scoringDispatcher = scoringDispatcher;
        this.privateMatchScoreRepository = privateMatchScoreRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.environment = environment;
    }

    /** 새벽 파이프라인을 수동으로 트리거한다 (백그라운드 실행). */
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

    /** 미분류 공고에 대해 직무 분류를 실행한다. */
    @Override
    @PostMapping("/classify")
    public ResponseEntity<String> classifyUnclassified() {
        int total = privateJobPostingService.classifyUnclassified(100);
        return ResponseEntity.ok("미분류 공고 " + total + "건 분류 완료");
    }

    /** 고용형태/경력 미분류 공고를 분류한다. */
    @Override
    @PostMapping("/classify-employment")
    public ResponseEntity<String> classifyMissingEmploymentTypes() {
        int total = privateJobPostingService.classifyMissingEmploymentTypes(100);
        return ResponseEntity.ok("고용형태/경력 미분류 공고 " + total + "건 분류 완료");
    }

    /** 지역 미분류 공고를 분류한다. */
    @Override
    @PostMapping("/classify-location")
    public ResponseEntity<String> classifyMissingRegions() {
        int total = privateJobPostingService.classifyMissingRegions(100);
        return ResponseEntity.ok("지역 미분류 공고 " + total + "건 분류 완료");
    }

    /** 미생성 공고 임베딩을 일괄 생성한다 (백그라운드 실행). */
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

    /** 사기업 매칭 점수를 동기 방식으로 산출한다 (백그라운드 실행, 시간 측정 포함). */
    @Override
    @PostMapping("/scoring")
    public ResponseEntity<String> scorePostings() {
        taskStatus.put("scoring", Map.of("status", "RUNNING"));
        schedulerTaskExecutor.execute(() -> {
            long startMs = System.currentTimeMillis();
            try {
                BatchNotificationHelper.BatchScoringResult result =
                        privateMatchBatchService.scoreNewAndUpdatedPostings();
                result.notifications().forEach((email, data) ->
                        batchNotificationHelper.sendIfNeeded(data.member(), data.postings(), "새 추천 공고"));
                long elapsedMs = System.currentTimeMillis() - startMs;
                String timedResult = result.summary() + String.format(" (소요: %dms)", elapsedMs);
                taskStatus.put("scoring", Map.of("status", "COMPLETED", "result", timedResult));
                log.info("[벤치마크] 동기 스코어링 완료: {} (소요: {}ms)", result.summary(), elapsedMs);
            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - startMs;
                taskStatus.put("scoring", Map.of(
                        "status", "FAILED",
                        "result", e.getMessage() + " (소요: " + elapsedMs + "ms)"));
                log.error("[수동트리거] 사기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body("사기업 매칭 점수 산출 시작됨 (백그라운드)");
    }

    /** 공기업 매칭 점수를 동기 방식으로 산출한다 (백그라운드 실행). */
    @Override
    @PostMapping("/scoring-public")
    public ResponseEntity<String> scorePublicPostings() {
        taskStatus.put("scoring-public", Map.of("status", "RUNNING"));
        schedulerTaskExecutor.execute(() -> {
            try {
                BatchNotificationHelper.BatchScoringResult result =
                        publicMatchBatchService.scoreNewAndUpdatedPostings();
                result.notifications().forEach((email, data) ->
                        batchNotificationHelper.sendIfNeeded(data.member(), data.postings(), "새 추천 공고"));
                taskStatus.put("scoring-public", Map.of("status", "COMPLETED", "result", result.summary()));
            } catch (Exception e) {
                taskStatus.put("scoring-public", Map.of("status", "FAILED", "result", e.getMessage()));
                log.error("[수동트리거] 공기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body("공기업 매칭 점수 산출 시작됨 (백그라운드)");
    }

    /** 미생성 이력서 임베딩을 일괄 생성한다. */
    @Override
    @PostMapping("/resume-embedding")
    public ResponseEntity<String> generateResumeEmbeddings() {
        String result = embeddingBatchService.generateMissingResumeEmbeddings();
        return ResponseEntity.ok(result);
    }

    /** IT 뉴스 카드를 수집·요약한다 (백그라운드 실행). */
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

    /** 기존 점수 기반 알림 테스트를 실행한다. email 파라미터로 특정 사용자만 지정 가능. */
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

    /**
     * Kafka 기반 병렬 스코어링을 실행한다.
     * 이벤트 발행 후 즉시 반환하며, 진행 상태는 {@code GET /status}에서 Redis를 조회하여 확인한다.
     */
    @Override
    @PostMapping("/scoring-kafka")
    public ResponseEntity<String> scorePostingsKafka() {
        ScoringDispatcher dispatcher = scoringDispatcher.getIfAvailable();
        if (dispatcher == null) {
            return ResponseEntity.badRequest()
                    .body("kafka 프로필이 활성화되지 않았습니다. --spring.profiles.active에 kafka를 추가하세요.");
        }

        taskStatus.put("scoring-kafka", Map.of("status", "RUNNING"));
        schedulerTaskExecutor.execute(() -> {
            long startMs = System.currentTimeMillis();
            try {
                ScoringDispatcher.DispatchResult dispatchResult = dispatcher.dispatchPrivateScoring();
                long dispatchElapsedMs = System.currentTimeMillis() - startMs;
                String dispatchMsg = String.format(
                        "Kafka 스코어링 이벤트 %d건 발행 완료 (발행 소요: %dms)",
                        dispatchResult.dispatched(), dispatchElapsedMs);
                log.info("[벤치마크] {}", dispatchMsg);

                if (dispatchResult.pipelineRunId() != null && dispatchResult.dispatched() > 0) {
                    // pipelineRunId를 저장해두면 /status 호출 시 Redis에서 진행률을 조회한다
                    taskStatus.put("scoring-kafka", Map.of(
                            "status", "PROCESSING",
                            "result", dispatchMsg,
                            "pipelineRunId", dispatchResult.pipelineRunId(),
                            "total", String.valueOf(dispatchResult.dispatched()),
                            "startMs", String.valueOf(startMs)));
                } else {
                    taskStatus.put("scoring-kafka", Map.of("status", "COMPLETED", "result", dispatchMsg));
                }
            } catch (Exception e) {
                long elapsedMs = System.currentTimeMillis() - startMs;
                taskStatus.put("scoring-kafka", Map.of(
                        "status", "FAILED",
                        "result", e.getMessage() + " (소요: " + elapsedMs + "ms)"));
                log.error("[벤치마크] Kafka 스코어링 발행 실패: {}", e.getMessage(), e);
            }
        });
        return ResponseEntity.accepted().body("[Kafka] 스코어링 이벤트 발행 시작됨 (백그라운드)");
    }

    /** 매칭 점수를 전체 초기화한다 (벤치마크용). local 프로필에서만 허용된다. */
    @Override
    @PostMapping("/reset-scores")
    public ResponseEntity<String> resetScores() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("local")) {
            return ResponseEntity.status(403)
                    .body("점수 초기화는 local 프로필에서만 허용됩니다.");
        }
        long count = privateMatchScoreRepository.count();
        privateMatchScoreRepository.deleteAllInBatch();
        return ResponseEntity.ok("매칭 점수 " + count + "건 초기화 완료");
    }

    /**
     * 비동기 작업들의 현재 상태를 조회한다.
     * scoring-kafka가 PROCESSING 상태이면 Redis에서 실시간 진행률을 조회하여 반환한다.
     */
    @Override
    @GetMapping("/status")
    public ResponseEntity<Map<String, Map<String, String>>> getTaskStatus() {
        Map<String, Map<String, String>> response = new ConcurrentHashMap<>(taskStatus);
        enrichKafkaScoringStatus(response);
        return ResponseEntity.ok(response);
    }

    /** scoring-kafka 항목이 PROCESSING이면 Redis 카운터를 조회하여 진행률·완료 판정을 반영한다. */
    private void enrichKafkaScoringStatus(Map<String, Map<String, String>> response) {
        Map<String, String> kafkaStatus = taskStatus.get("scoring-kafka");
        if (kafkaStatus == null || !"PROCESSING".equals(kafkaStatus.get("status"))) return;

        String pipelineRunId = kafkaStatus.get("pipelineRunId");
        String totalStr = kafkaStatus.get("total");
        if (pipelineRunId == null || totalStr == null) return;

        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) return;

        String prefix = "jobai:scoring:" + pipelineRunId;
        String completedStr = redis.opsForValue().get(prefix + ":completed");
        String resultStr = redis.opsForValue().get(prefix + ":result");

        if (resultStr != null) {
            // Consumer가 완료 판정을 기록함
            taskStatus.put("scoring-kafka", Map.of("status", "COMPLETED", "result", resultStr));
            response.put("scoring-kafka", Map.of("status", "COMPLETED", "result", resultStr));
        } else if (completedStr != null && Long.parseLong(completedStr) >= Long.parseLong(totalStr)) {
            String startMsStr = kafkaStatus.get("startMs");
            long elapsed = startMsStr != null ? System.currentTimeMillis() - Long.parseLong(startMsStr) : 0;
            String finalResult = String.format(
                    "Kafka 스코어링 전체 완료: %s건 처리 (총 소요: %dms)", totalStr, elapsed);
            taskStatus.put("scoring-kafka", Map.of("status", "COMPLETED", "result", finalResult));
            response.put("scoring-kafka", Map.of("status", "COMPLETED", "result", finalResult));
        } else {
            String progress = String.format("%s — 진행: %s/%s건 완료",
                    kafkaStatus.get("result"), completedStr != null ? completedStr : "0", totalStr);
            response.put("scoring-kafka", Map.of("status", "PROCESSING", "result", progress));
        }
    }
}

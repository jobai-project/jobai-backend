package com.jobai.backend.domain.privatejobposting.scheduler;

import com.jobai.backend.domain.privatejobposting.service.PrivateJobBatchCollectService;
import com.jobai.backend.domain.privatejobposting.service.PrivateJobPostingService;
import com.jobai.backend.domain.matching.service.BatchNotificationHelper;
import com.jobai.backend.domain.matching.service.PrivateMatchBatchService;
import com.jobai.backend.domain.matching.service.PublicMatchBatchService;
import com.jobai.backend.domain.matching.service.ScoringDispatcher;
import com.jobai.backend.domain.publicInstitution.service.JobDataSyncService;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import com.jobai.backend.global.cache.PipelineCacheEvictionEvent;
import com.jobai.backend.global.kafka.event.PipelineStageCompleteEvent;
import com.jobai.backend.global.kafka.producer.KafkaPipelineProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * 새벽 2시(KST) 전체 파이프라인을 순차 실행하는 스케줄러.
 *
 * <pre>
 * Step 1: 사기업 공고 수집
 * Step 2: 공기업 공고 수집
 * Step 3: 직무/고용형태/경력 분류
 * Step 4: 지역 분류
 * Step 5: 이력서 임베딩 복구
 * Step 6: 공고 임베딩 생성 (전체 반복 처리)
 * Step 7: 신규/변경 공고 매칭 점수 산출
 * </pre>
 *
 * <p>{@code scheduler.daily.enabled=false}로 비활성화할 수 있다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scheduler.daily.enabled", havingValue = "true", matchIfMissing = true)
public class DailyJobScheduler {

    private final PrivateJobBatchCollectService privateJobBatchCollectService;
    private final PrivateJobPostingService privateJobPostingService;
    private final JobDataSyncService jobDataSyncService;
    private final EmbeddingBatchService embeddingBatchService;
    private final PrivateMatchBatchService privateMatchBatchService;
    private final PublicMatchBatchService publicMatchBatchService;
    private final BatchNotificationHelper batchNotificationHelper;
    private final ObjectProvider<ScoringDispatcher> scoringDispatcher;
    private final ObjectProvider<KafkaPipelineProducer> kafkaPipelineProducer;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean kafkaScoringEnabled;
    private final boolean kafkaPipelineEnabled;

    public DailyJobScheduler(
            PrivateJobBatchCollectService privateJobBatchCollectService,
            PrivateJobPostingService privateJobPostingService,
            JobDataSyncService jobDataSyncService,
            EmbeddingBatchService embeddingBatchService,
            PrivateMatchBatchService privateMatchBatchService,
            PublicMatchBatchService publicMatchBatchService,
            BatchNotificationHelper batchNotificationHelper,
            ObjectProvider<ScoringDispatcher> scoringDispatcher,
            ObjectProvider<KafkaPipelineProducer> kafkaPipelineProducer,
            ApplicationEventPublisher eventPublisher,
            @Value("${kafka.scoring.enabled:false}") boolean kafkaScoringEnabled,
            @Value("${kafka.pipeline.enabled:false}") boolean kafkaPipelineEnabled
    ) {
        this.privateJobBatchCollectService = privateJobBatchCollectService;
        this.privateJobPostingService = privateJobPostingService;
        this.jobDataSyncService = jobDataSyncService;
        this.embeddingBatchService = embeddingBatchService;
        this.privateMatchBatchService = privateMatchBatchService;
        this.publicMatchBatchService = publicMatchBatchService;
        this.batchNotificationHelper = batchNotificationHelper;
        this.scoringDispatcher = scoringDispatcher;
        this.kafkaPipelineProducer = kafkaPipelineProducer;
        this.eventPublisher = eventPublisher;
        this.kafkaScoringEnabled = kafkaScoringEnabled;
        this.kafkaPipelineEnabled = kafkaPipelineEnabled;
    }

    /**
     * 7단계 파이프라인을 순차 실행한다.
     * 각 단계는 독립된 try-catch로 감싸져 있어, 하나가 실패해도 나머지 단계는 계속 실행된다.
     *
     * @return 각 단계 결과를 요약한 문자열
     */
    @Scheduled(cron = "${scheduler.daily.cron:0 0 2 * * *}", zone = "Asia/Seoul")
    public String runDailyPipeline() {
        log.info("[DailyPipeline] ===== 새벽 파이프라인 시작 =====");
        long start = System.currentTimeMillis();

        int privateCollected = 0;
        int publicCollected = 0;
        int classified = 0;
        int embeddingCount = 0;
        String resumeEmbeddingResult = "-";

        // Step 1: 사기업 수집
        try {
            log.info("[DailyPipeline] Step 1/7 — 사기업 공고 수집 시작");
            privateCollected = privateJobBatchCollectService.collectAll();
            log.info("[DailyPipeline] Step 1/7 — 사기업 공고 수집 완료: {}건", privateCollected);
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 1/7 — 사기업 공고 수집 실패: {}", e.getMessage(), e);
        }

        // Step 2: 공기업 수집
        try {
            log.info("[DailyPipeline] Step 2/7 — 공기업 공고 수집 시작");
            publicCollected = jobDataSyncService.syncPublicJobOpenings();
            log.info("[DailyPipeline] Step 2/7 — 공기업 공고 수집 완료: {}건", publicCollected);
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 2/7 — 공기업 공고 수집 실패: {}", e.getMessage(), e);
        }

        // Kafka 파이프라인 모드: 수집 완료 후 나머지를 Orchestrator에게 위임
        KafkaPipelineProducer pipelineProducer = kafkaPipelineProducer.getIfAvailable();
        if (kafkaPipelineEnabled && pipelineProducer != null) {
            String pipelineRunId = UUID.randomUUID().toString();
            log.info("[DailyPipeline] Kafka 파이프라인 모드 — 수집 완료 이벤트 발행, pipelineRunId={}",
                    pipelineRunId);
            try {
                pipelineProducer.sendStageComplete(new PipelineStageCompleteEvent(
                        pipelineRunId,
                        PipelineStageCompleteEvent.COLLECTION,
                        privateCollected + publicCollected,
                        String.format("사기업 %d건, 공기업 %d건", privateCollected, publicCollected),
                        Instant.now()
                )).get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("[DailyPipeline] 수집 완료 이벤트 발행 실패: {}", e.getMessage(), e);
            }

            // 캐시 무효화는 Kafka Orchestrator 최종 단계(onEmbeddingComplete)에서 수행

            long elapsed = System.currentTimeMillis() - start;
            log.info("[DailyPipeline] ===== 수집 완료, 나머지는 Kafka Orchestrator가 처리 ({}ms) =====", elapsed);
            return String.format("Kafka 파이프라인: 수집 사기업 %d건, 공기업 %d건 | 소요: %dms",
                    privateCollected, publicCollected, elapsed);
        }

        // ── 기존 동기 경로 (kafka.pipeline.enabled=false) ──

        // Step 3: 직무/고용형태/경력 분류
        try {
            log.info("[DailyPipeline] Step 3/7 — 직무/고용형태/경력 분류 시작");
            classified += privateJobPostingService.classifyUnclassified(100);
            classified += privateJobPostingService.classifyMissingEmploymentTypes(100);
            log.info("[DailyPipeline] Step 3/7 — 직무/고용형태/경력 분류 완료 ({}건)", classified);
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 3/7 — 직무/고용형태/경력 분류 실패: {}", e.getMessage(), e);
        }

        // Step 4: 지역 분류
        try {
            log.info("[DailyPipeline] Step 4/7 — 지역 분류 시작");
            classified += privateJobPostingService.classifyMissingRegions(100);
            log.info("[DailyPipeline] Step 4/7 — 지역 분류 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 4/7 — 지역 분류 실패: {}", e.getMessage(), e);
        }

        // Step 5: 이력서 임베딩 복구 (업로드 시 실패한 이력서의 임베딩을 자동 복구)
        try {
            log.info("[DailyPipeline] Step 5/7 — 이력서 임베딩 복구 시작");
            resumeEmbeddingResult = embeddingBatchService.generateMissingResumeEmbeddings();
            log.info("[DailyPipeline] Step 5/7 — 이력서 임베딩 복구 완료: {}", resumeEmbeddingResult);
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 5/7 — 이력서 임베딩 복구 실패: {}", e.getMessage(), e);
        }

        // Step 6: 공고 임베딩 생성 (미생성 건 전체를 반복 처리)
        try {
            log.info("[DailyPipeline] Step 6/7 — 공고 임베딩 생성 시작");
            embeddingBatchService.generateAllMissingEmbeddings();
            log.info("[DailyPipeline] Step 6/7 — 공고 임베딩 생성 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 6/7 — 공고 임베딩 생성 실패: {}", e.getMessage(), e);
        }

        // Step 7: 매칭 점수 산출
        ScoringDispatcher dispatcher = scoringDispatcher.getIfAvailable();
        if (kafkaScoringEnabled && dispatcher != null) {
            // Kafka 경로: 사기업은 이벤트 발행 → Consumer가 병렬 처리
            try {
                log.info("[DailyPipeline] Step 7/7 — Kafka 사기업 스코어링 이벤트 발행 시작");
                ScoringDispatcher.DispatchResult dispatchResult = dispatcher.dispatchPrivateScoring();
                log.info("[DailyPipeline] Step 7/7 — Kafka 사기업 스코어링 이벤트 발행 완료: {}건", dispatchResult.dispatched());
            } catch (Exception e) {
                log.error("[DailyPipeline] Step 7/7 — Kafka 사기업 스코어링 발행 실패: {}", e.getMessage(), e);
            }

            // 공기업은 Kafka 디스패처 미구현이므로 동기 처리 (알림은 Kafka 스코어링 완료 시 배치 발송)
            try {
                log.info("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 시작 (동기)");
                publicMatchBatchService.scoreNewAndUpdatedPostings();
                log.info("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 완료");
            } catch (Exception e) {
                log.error("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
            }
        } else {
            // 기존 경로: 동기 순차 처리
            Map<String, BatchNotificationHelper.MemberNotifications> combinedNotifications = new LinkedHashMap<>();

            try {
                log.info("[DailyPipeline] Step 7/7 — 사기업 매칭 점수 산출 시작");
                BatchNotificationHelper.BatchScoringResult privateResult =
                        privateMatchBatchService.scoreNewAndUpdatedPostings();
                privateResult.notifications().forEach((email, data) ->
                        combinedNotifications.merge(email, data, (existing, incoming) -> {
                            List<BatchNotificationHelper.ScoredPosting> merged = new ArrayList<>(existing.postings());
                            merged.addAll(incoming.postings());
                            return new BatchNotificationHelper.MemberNotifications(existing.member(), merged);
                        }));
                log.info("[DailyPipeline] Step 7/7 — 사기업 매칭 점수 산출 완료");
            } catch (Exception e) {
                log.error("[DailyPipeline] Step 7/7 — 사기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
            }

            try {
                log.info("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 시작");
                BatchNotificationHelper.BatchScoringResult publicResult =
                        publicMatchBatchService.scoreNewAndUpdatedPostings();
                publicResult.notifications().forEach((email, data) ->
                        combinedNotifications.merge(email, data, (existing, incoming) -> {
                            List<BatchNotificationHelper.ScoredPosting> merged = new ArrayList<>(existing.postings());
                            merged.addAll(incoming.postings());
                            return new BatchNotificationHelper.MemberNotifications(existing.member(), merged);
                        }));
                log.info("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 완료");
            } catch (Exception e) {
                log.error("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
            }

            combinedNotifications.forEach((email, data) ->
                    batchNotificationHelper.sendIfNeeded(data.member(), data.postings(), "새 추천 공고"));
        }

        eventPublisher.publishEvent(new PipelineCacheEvictionEvent(this));

        long elapsed = System.currentTimeMillis() - start;
        log.info("[DailyPipeline] ===== 새벽 파이프라인 종료 ({}ms) =====", elapsed);

        return String.format("수집: 사기업 %d건, 공기업 %d건 | 분류: %d건 | 이력서임베딩: %s | 소요: %dms",
                privateCollected, publicCollected, classified, resumeEmbeddingResult, elapsed);
    }
}

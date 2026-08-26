package com.jobai.backend.global.kafka.consumer;

import com.jobai.backend.domain.matching.service.PublicMatchBatchService;
import com.jobai.backend.domain.matching.service.ScoringDispatcher;
import com.jobai.backend.domain.privatejobposting.service.PrivateJobPostingService;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import com.jobai.backend.global.kafka.event.PipelineStageCompleteEvent;
import com.jobai.backend.global.kafka.producer.KafkaPipelineProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaPipelineOrchestrator {

    private final PrivateJobPostingService privateJobPostingService;
    private final EmbeddingBatchService embeddingBatchService;
    private final ScoringDispatcher scoringDispatcher;
    private final PublicMatchBatchService publicMatchBatchService;
    private final KafkaPipelineProducer pipelineProducer;

    /**
     * 수집 완료 → 분류 실행 → classification-complete 발행.
     * 각 분류 단계를 개별 try-catch로 감싸서 하나가 실패해도 나머지를 계속 처리한다.
     * 이벤트 발행 실패 시 예외를 전파하여 DefaultErrorHandler가 재시도하도록 한다.
     */
    @KafkaListener(
            topics = "jobai.pipeline.collection-complete",
            groupId = "jobai-pipeline-group",
            properties = {
                    "spring.json.value.default.type=com.jobai.backend.global.kafka.event.PipelineStageCompleteEvent"
            }
    )
    public void onCollectionComplete(PipelineStageCompleteEvent event) {
        log.info("[파이프라인] 수집 완료 수신 → 분류 시작: pipelineRunId={}", event.pipelineRunId());

        int classified = 0;
        int failures = 0;

        try {
            classified += privateJobPostingService.classifyUnclassified(100);
        } catch (Exception e) {
            failures++;
            log.error("[파이프라인] 직무 분류 실패: {}", e.getMessage(), e);
        }

        try {
            classified += privateJobPostingService.classifyMissingEmploymentTypes(100);
        } catch (Exception e) {
            failures++;
            log.error("[파이프라인] 고용형태/경력 분류 실패: {}", e.getMessage(), e);
        }

        try {
            classified += privateJobPostingService.classifyMissingRegions(100);
        } catch (Exception e) {
            failures++;
            log.error("[파이프라인] 지역 분류 실패: {}", e.getMessage(), e);
        }

        String detail = String.format("분류 %d건 처리%s",
                classified, failures > 0 ? ", 실패 " + failures + "건" : "");
        log.info("[파이프라인] 분류 완료: {} → classification-complete 발행", detail);

        awaitSend(new PipelineStageCompleteEvent(
                event.pipelineRunId(),
                PipelineStageCompleteEvent.CLASSIFICATION,
                classified,
                detail,
                Instant.now()
        ));
    }

    /**
     * 분류 완료 → 임베딩 실행 → embedding-complete 발행.
     * 이력서/공고 임베딩을 개별 try-catch로 감싸서 부분 실패에도 다음 단계로 진행한다.
     */
    @KafkaListener(
            topics = "jobai.pipeline.classification-complete",
            groupId = "jobai-pipeline-group",
            properties = {
                    "spring.json.value.default.type=com.jobai.backend.global.kafka.event.PipelineStageCompleteEvent"
            }
    )
    public void onClassificationComplete(PipelineStageCompleteEvent event) {
        log.info("[파이프라인] 분류 완료 수신 → 임베딩 시작: pipelineRunId={}", event.pipelineRunId());

        String resumeResult = "-";
        try {
            resumeResult = embeddingBatchService.generateMissingResumeEmbeddings();
        } catch (Exception e) {
            log.error("[파이프라인] 이력서 임베딩 복구 실패: {}", e.getMessage(), e);
        }

        try {
            embeddingBatchService.generateAllMissingEmbeddings();
        } catch (Exception e) {
            log.error("[파이프라인] 공고 임베딩 생성 실패: {}", e.getMessage(), e);
        }

        log.info("[파이프라인] 임베딩 완료: 이력서={} → embedding-complete 발행", resumeResult);

        awaitSend(new PipelineStageCompleteEvent(
                event.pipelineRunId(),
                PipelineStageCompleteEvent.EMBEDDING,
                0,
                "임베딩 완료, 이력서: " + resumeResult,
                Instant.now()
        ));
    }

    /**
     * 임베딩 완료 → 스코어링 이벤트 발행.
     * 상위 pipelineRunId를 ScoringDispatcher에 전달하여 파이프라인 추적을 유지한다.
     */
    @KafkaListener(
            topics = "jobai.pipeline.embedding-complete",
            groupId = "jobai-pipeline-group",
            properties = {
                    "spring.json.value.default.type=com.jobai.backend.global.kafka.event.PipelineStageCompleteEvent"
            }
    )
    public void onEmbeddingComplete(PipelineStageCompleteEvent event) {
        log.info("[파이프라인] 임베딩 완료 수신 → 스코어링 시작: pipelineRunId={}", event.pipelineRunId());

        // 사기업: Kafka 병렬 처리 — 상위 pipelineRunId 전달
        Exception dispatchException = null;
        try {
            ScoringDispatcher.DispatchResult result =
                    scoringDispatcher.dispatchPrivateScoring(event.pipelineRunId());
            log.info("[파이프라인] 사기업 스코어링 이벤트 발행 완료: {}건", result.dispatched());
        } catch (Exception e) {
            log.error("[파이프라인] 사기업 스코어링 발행 실패: {}", e.getMessage(), e);
            dispatchException = e;
        }

        // 공기업: Kafka 디스패처 미구현이므로 동기 처리 (알림은 Kafka 스코어링 완료 시 배치 발송)
        try {
            log.info("[파이프라인] 공기업 매칭 점수 산출 시작 (동기)");
            publicMatchBatchService.scoreNewAndUpdatedPostings();
            log.info("[파이프라인] 공기업 매칭 점수 산출 완료");
        } catch (Exception e) {
            log.error("[파이프라인] 공기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
        }

        // 사기업 디스패치 실패 시 예외를 전파하여 DefaultErrorHandler가 재시도하도록 한다
        if (dispatchException != null) {
            throw new RuntimeException("[파이프라인] 사기업 스코어링 발행 실패", dispatchException);
        }
    }

    /**
     * 이벤트 발행을 동기 대기한다. 발행 실패 시 예외를 전파하여
     * DefaultErrorHandler의 재시도와 DLT 처리가 실행되도록 한다.
     */
    private void awaitSend(PipelineStageCompleteEvent event) {
        try {
            pipelineProducer.sendStageComplete(event).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[파이프라인] 이벤트 발행 인터럽트: " + event.stage(), e);
        } catch (ExecutionException e) {
            throw new RuntimeException("[파이프라인] 이벤트 발행 실패: " + event.stage(), e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException("[파이프라인] 이벤트 발행 타임아웃: " + event.stage(), e);
        }
    }
}

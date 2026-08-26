package com.jobai.backend.global.kafka.consumer;

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

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaPipelineOrchestrator {

    private final PrivateJobPostingService privateJobPostingService;
    private final EmbeddingBatchService embeddingBatchService;
    private final ScoringDispatcher scoringDispatcher;
    private final KafkaPipelineProducer pipelineProducer;

    /**
     * 수집 완료 → 분류 실행 → classification-complete 발행
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
        try {
            classified += privateJobPostingService.classifyUnclassified(100);
            classified += privateJobPostingService.classifyMissingEmploymentTypes(100);
            classified += privateJobPostingService.classifyMissingRegions(100);
        } catch (Exception e) {
            log.error("[파이프라인] 분류 실패: {}", e.getMessage(), e);
        }

        log.info("[파이프라인] 분류 완료: {}건 → embedding-complete 발행", classified);

        pipelineProducer.sendStageComplete(new PipelineStageCompleteEvent(
                event.pipelineRunId(),
                PipelineStageCompleteEvent.CLASSIFICATION,
                classified,
                "분류 " + classified + "건 처리",
                Instant.now()
        ));
    }

    /**
     * 분류 완료 → 임베딩 실행 → embedding-complete 발행
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

        pipelineProducer.sendStageComplete(new PipelineStageCompleteEvent(
                event.pipelineRunId(),
                PipelineStageCompleteEvent.EMBEDDING,
                0,
                "임베딩 완료, 이력서: " + resumeResult,
                Instant.now()
        ));
    }

    /**
     * 임베딩 완료 → 스코어링 이벤트 발행 (ScoringDispatcher가 Kafka로 병렬 처리)
     */
    @KafkaListener(
            topics = "jobai.pipeline.embedding-complete",
            groupId = "jobai-pipeline-group",
            properties = {
                    "spring.json.value.default.type=com.jobai.backend.global.kafka.event.PipelineStageCompleteEvent"
            }
    )
    public void onEmbeddingComplete(PipelineStageCompleteEvent event) {
        log.info("[파이프라인] 임베딩 완료 수신 → 스코어링 발행 시작: pipelineRunId={}", event.pipelineRunId());

        try {
            ScoringDispatcher.DispatchResult result = scoringDispatcher.dispatchPrivateScoring();
            log.info("[파이프라인] 스코어링 이벤트 발행 완료: {}건", result.dispatched());
        } catch (Exception e) {
            log.error("[파이프라인] 스코어링 발행 실패: {}", e.getMessage(), e);
        }
    }
}

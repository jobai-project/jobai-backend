package com.jobai.backend.domain.privatejobposting.scheduler;

import com.jobai.backend.domain.privatejobposting.service.PrivateJobBatchCollectService;
import com.jobai.backend.domain.privatejobposting.service.PrivateJobPostingService;
import com.jobai.backend.domain.matching.service.PrivateMatchBatchService;
import com.jobai.backend.domain.matching.service.PublicMatchBatchService;
import com.jobai.backend.domain.publicInstitution.service.JobDataSyncService;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 새벽 2시(KST) 전체 파이프라인�� 순차 실행하는 스케줄러.
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
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.daily.enabled", havingValue = "true", matchIfMissing = true)
public class DailyJobScheduler {

    private final PrivateJobBatchCollectService privateJobBatchCollectService;
    private final PrivateJobPostingService privateJobPostingService;
    private final JobDataSyncService jobDataSyncService;
    private final EmbeddingBatchService embeddingBatchService;
    private final PrivateMatchBatchService privateMatchBatchService;
    private final PublicMatchBatchService publicMatchBatchService;

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

        // Step 7: 매칭 점수 산출 (신규/변경 공고만, 사기업/공기업 각각 독립 실행)
        try {
            log.info("[DailyPipeline] Step 7/7 — 사기업 매칭 점수 산출 시작");
            privateMatchBatchService.scoreNewAndUpdatedPostings();
            log.info("[DailyPipeline] Step 7/7 — 사기업 매칭 점수 산출 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 7/7 — 사기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
        }

        try {
            log.info("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 시작");
            publicMatchBatchService.scoreNewAndUpdatedPostings();
            log.info("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 7/7 — 공기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[DailyPipeline] ===== 새벽 파이프라인 종료 ({}ms) =====", elapsed);

        return String.format("수집: 사기업 %d건, 공기업 %d건 | 분류: %d건 | 이력서임베딩: %s | 소요: %dms",
                privateCollected, publicCollected, classified, resumeEmbeddingResult, elapsed);
    }
}

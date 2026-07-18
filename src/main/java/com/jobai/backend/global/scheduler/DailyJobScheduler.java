package com.jobai.backend.global.scheduler;

import com.jobai.backend.domain.privatejobposting.service.PrivateJobBatchCollectService;
import com.jobai.backend.domain.privatejobposting.service.PrivateJobPostingService;
import com.jobai.backend.domain.home.service.PrivateMatchBatchService;
import com.jobai.backend.domain.home.service.PublicMatchBatchService;
import com.jobai.backend.domain.publicInstitution.service.JobDataSyncService;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 새벽 2시(KST) 전체 파이프라인을 순차 실행하는 스케줄러.
 *
 * <pre>
 * Step 1: 사기업 공고 수집
 * Step 2: 공기업 공고 수집
 * Step 3: 직무/고용형태/경력 분류
 * Step 4: 지역 분류
 * Step 5: 임베딩 생성
 * Step 6: 신규/변경 공고 매칭 점수 산출
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
     * 새벽 파이프라인을 실행한다.
     *
     * <p>각 단계는 독립적으로 try-catch 되어 있어, 앞 단계가 실패해도 뒷 단계는 정상 진행된다.
     * cron 표현식은 {@code scheduler.daily.cron} 프로퍼티로 외부화되어 있다.</p>
     *
     * @see PrivateJobBatchCollectService#collectAll()
     * @see JobDataSyncService#syncPublicJobOpenings()
     * @see EmbeddingBatchService#generateMissingEmbeddings()
     * @see PrivateMatchBatchService#scoreNewAndUpdatedPostings()
     * @see PublicMatchBatchService#scoreNewAndUpdatedPostings()
     */
    @Scheduled(cron = "${scheduler.daily.cron:0 0 2 * * *}", zone = "Asia/Seoul")
    public void runDailyPipeline() {
        log.info("[DailyPipeline] ===== 새벽 파이프라인 시작 =====");
        long start = System.currentTimeMillis();

        // Step 1: 사기업 수집
        try {
            log.info("[DailyPipeline] Step 1/6 — 사기업 공고 수집 시작");
            privateJobBatchCollectService.collectAll();
            log.info("[DailyPipeline] Step 1/6 — 사기업 공고 수집 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 1/6 — 사기업 공고 수집 실패: {}", e.getMessage(), e);
        }

        // Step 2: 공기업 수집
        try {
            log.info("[DailyPipeline] Step 2/6 — 공기업 공고 수집 시작");
            jobDataSyncService.syncPublicJobOpenings();
            log.info("[DailyPipeline] Step 2/6 — 공기업 공고 수집 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 2/6 — 공기업 공고 수집 실패: {}", e.getMessage(), e);
        }

        // Step 3: 직무/고용형태/경력 분류
        try {
            log.info("[DailyPipeline] Step 3/6 — 직무/고용형태/경력 분류 시작");
            int classified = privateJobPostingService.classifyUnclassified(100);
            int empClassified = privateJobPostingService.classifyMissingEmploymentTypes(100);
            log.info("[DailyPipeline] Step 3/6 — 직무/고용형태/경력 분류 완료 (직무 {}건, 고용형태/경력 {}건)", classified, empClassified);
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 3/6 — 직무/고용형태/경력 분류 실패: {}", e.getMessage(), e);
        }

        // Step 4: 지역 분류
        try {
            log.info("[DailyPipeline] Step 4/6 — 지역 분류 시작");
            int regionClassified = privateJobPostingService.classifyMissingRegions(100);
            log.info("[DailyPipeline] Step 4/6 — 지역 분류 완료 ({}건)", regionClassified);
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 4/6 — 지역 분류 실패: {}", e.getMessage(), e);
        }

        // Step 5: 임베딩 생성
        try {
            log.info("[DailyPipeline] Step 5/6 — 임베딩 생성 시작");
            embeddingBatchService.generateMissingEmbeddings();
            log.info("[DailyPipeline] Step 5/6 — 임베딩 생성 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 5/6 — 임베딩 생성 실패: {}", e.getMessage(), e);
        }

        // Step 6: 매칭 점수 산출 (신규/변경 공고만, 사기업/공기업 각각 독립 실행)
        try {
            log.info("[DailyPipeline] Step 6/6 — 사기업 매칭 점수 산출 시작");
            privateMatchBatchService.scoreNewAndUpdatedPostings();
            log.info("[DailyPipeline] Step 6/6 — 사기업 매칭 점수 산출 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 6/6 — 사기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
        }

        try {
            log.info("[DailyPipeline] Step 6/6 — 공기업 매칭 점수 산출 시작");
            publicMatchBatchService.scoreNewAndUpdatedPostings();
            log.info("[DailyPipeline] Step 6/6 — 공기업 매칭 점수 산출 완료");
        } catch (Exception e) {
            log.error("[DailyPipeline] Step 6/6 — 공기업 매칭 점수 산출 실패: {}", e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[DailyPipeline] ===== 새벽 파이프라인 종료 ({}ms) =====", elapsed);
    }
}

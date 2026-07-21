package com.jobai.backend.domain.privatejobposting.scheduler;

import com.jobai.backend.domain.privatejobposting.service.PrivateJobBatchCollectService;
import com.jobai.backend.domain.privatejobposting.service.PrivateJobPostingService;
import com.jobai.backend.domain.matching.service.BatchNotificationHelper;
import com.jobai.backend.domain.matching.service.PrivateMatchBatchService;
import com.jobai.backend.domain.matching.service.PublicMatchBatchService;
import com.jobai.backend.domain.publicInstitution.service.JobDataSyncService;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

class
DailyJobSchedulerTest {

    private PrivateJobBatchCollectService privateJobBatchCollectService;
    private PrivateJobPostingService privateJobPostingService;
    private JobDataSyncService jobDataSyncService;
    private EmbeddingBatchService embeddingBatchService;
    private PrivateMatchBatchService privateMatchBatchService;
    private PublicMatchBatchService publicMatchBatchService;
    private BatchNotificationHelper batchNotificationHelper;

    private DailyJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        privateJobBatchCollectService = Mockito.mock(PrivateJobBatchCollectService.class);
        privateJobPostingService = Mockito.mock(PrivateJobPostingService.class);
        jobDataSyncService = Mockito.mock(JobDataSyncService.class);
        embeddingBatchService = Mockito.mock(EmbeddingBatchService.class);
        privateMatchBatchService = Mockito.mock(PrivateMatchBatchService.class);
        publicMatchBatchService = Mockito.mock(PublicMatchBatchService.class);
        batchNotificationHelper = Mockito.mock(BatchNotificationHelper.class);

        Mockito.when(privateMatchBatchService.scoreNewAndUpdatedPostings())
                .thenReturn(new BatchNotificationHelper.BatchScoringResult("", java.util.Map.of()));
        Mockito.when(publicMatchBatchService.scoreNewAndUpdatedPostings())
                .thenReturn(new BatchNotificationHelper.BatchScoringResult("", java.util.Map.of()));

        scheduler = new DailyJobScheduler(
                privateJobBatchCollectService,
                privateJobPostingService,
                jobDataSyncService,
                embeddingBatchService,
                privateMatchBatchService,
                publicMatchBatchService,
                batchNotificationHelper
        );
    }

    @Test
    @DisplayName("정상 실행 시 7개 단계가 순서대로 호출된다")
    void runDailyPipeline_정상실행_순서검증() {
        scheduler.runDailyPipeline();

        InOrder inOrder = inOrder(
                privateJobBatchCollectService, privateJobPostingService, jobDataSyncService,
                embeddingBatchService, privateMatchBatchService, publicMatchBatchService);
        inOrder.verify(privateJobBatchCollectService).collectAll();
        inOrder.verify(jobDataSyncService).syncPublicJobOpenings();
        inOrder.verify(privateJobPostingService).classifyUnclassified(100);
        inOrder.verify(privateJobPostingService).classifyMissingEmploymentTypes(100);
        inOrder.verify(privateJobPostingService).classifyMissingRegions(100);
        inOrder.verify(embeddingBatchService).generateMissingResumeEmbeddings();
        inOrder.verify(embeddingBatchService).generateAllMissingEmbeddings();
        inOrder.verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
        inOrder.verify(publicMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 1(사기업 수집) 실패 시에도 나머지 단계가 실행된다")
    void runDailyPipeline_step1실패_나머지실행() {
        doThrow(new RuntimeException("수집 실패"))
                .when(privateJobBatchCollectService).collectAll();

        scheduler.runDailyPipeline();

        verify(jobDataSyncService).syncPublicJobOpenings();
        verify(embeddingBatchService).generateMissingResumeEmbeddings();
        verify(embeddingBatchService).generateAllMissingEmbeddings();
        verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
        verify(publicMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 2(공기업 수집) 실패 시에도 나머지 단계가 실행된다")
    void runDailyPipeline_step2실패_나머지실행() {
        doThrow(new RuntimeException("공기업 수집 실패"))
                .when(jobDataSyncService).syncPublicJobOpenings();

        scheduler.runDailyPipeline();

        verify(privateJobBatchCollectService).collectAll();
        verify(embeddingBatchService).generateMissingResumeEmbeddings();
        verify(embeddingBatchService).generateAllMissingEmbeddings();
        verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
        verify(publicMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 5(이력서 임베딩 복구) 실패 시에도 나머지 단계가 실행된다")
    void runDailyPipeline_step5실패_나머지실행() {
        doThrow(new RuntimeException("이력서 임베딩 실패"))
                .when(embeddingBatchService).generateMissingResumeEmbeddings();

        scheduler.runDailyPipeline();

        verify(privateJobBatchCollectService).collectAll();
        verify(jobDataSyncService).syncPublicJobOpenings();
        verify(embeddingBatchService).generateAllMissingEmbeddings();
        verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
        verify(publicMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 6(공고 임베딩) 실패 시에도 Step 7이 실행된다")
    void runDailyPipeline_step6실패_나머지실행() {
        doThrow(new RuntimeException("공고 임베딩 실패"))
                .when(embeddingBatchService).generateAllMissingEmbeddings();

        scheduler.runDailyPipeline();

        verify(privateJobBatchCollectService).collectAll();
        verify(jobDataSyncService).syncPublicJobOpenings();
        verify(embeddingBatchService).generateMissingResumeEmbeddings();
        verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
        verify(publicMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 7 사기업 매칭 점수 산출 실패 시에도 공기업 매칭 점수 산출은 실행된다")
    void runDailyPipeline_step7사기업실패_공기업은계속() {
        doThrow(new RuntimeException("사기업 점수 산출 실패"))
                .when(privateMatchBatchService).scoreNewAndUpdatedPostings();

        scheduler.runDailyPipeline();

        verify(privateJobBatchCollectService).collectAll();
        verify(jobDataSyncService).syncPublicJobOpenings();
        verify(embeddingBatchService).generateMissingResumeEmbeddings();
        verify(embeddingBatchService).generateAllMissingEmbeddings();
        verify(publicMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 7 공기업 매칭 점수 산출 실패 시에도 예외 전파 없이 정상 종료된다")
    void runDailyPipeline_step7공기업실패_정상종료() {
        doThrow(new RuntimeException("공기업 점수 산출 실패"))
                .when(publicMatchBatchService).scoreNewAndUpdatedPostings();

        scheduler.runDailyPipeline();

        verify(privateJobBatchCollectService).collectAll();
        verify(jobDataSyncService).syncPublicJobOpenings();
        verify(embeddingBatchService).generateMissingResumeEmbeddings();
        verify(embeddingBatchService).generateAllMissingEmbeddings();
        verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
    }
}

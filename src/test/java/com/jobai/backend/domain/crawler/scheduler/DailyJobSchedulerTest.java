package com.jobai.backend.domain.crawler.scheduler;

import com.jobai.backend.domain.crawler.service.PrivateJobBatchCollectService;
import com.jobai.backend.domain.home.service.PrivateMatchBatchService;
import com.jobai.backend.domain.publicInstitution.service.JobDataSyncService;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

class DailyJobSchedulerTest {

    private PrivateJobBatchCollectService privateJobBatchCollectService;
    private JobDataSyncService jobDataSyncService;
    private EmbeddingBatchService embeddingBatchService;
    private PrivateMatchBatchService privateMatchBatchService;

    private DailyJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        privateJobBatchCollectService = Mockito.mock(PrivateJobBatchCollectService.class);
        jobDataSyncService = Mockito.mock(JobDataSyncService.class);
        embeddingBatchService = Mockito.mock(EmbeddingBatchService.class);
        privateMatchBatchService = Mockito.mock(PrivateMatchBatchService.class);

        scheduler = new DailyJobScheduler(
                privateJobBatchCollectService,
                jobDataSyncService,
                embeddingBatchService,
                privateMatchBatchService
        );
    }

    @Test
    @DisplayName("정상 실행 시 4개 단계가 순서대로 호출된다")
    void runDailyPipeline_정상실행_순서검증() {
        scheduler.runDailyPipeline();

        InOrder inOrder = inOrder(
                privateJobBatchCollectService, jobDataSyncService,
                embeddingBatchService, privateMatchBatchService);
        inOrder.verify(privateJobBatchCollectService).collectAll();
        inOrder.verify(jobDataSyncService).syncPublicJobOpenings();
        inOrder.verify(embeddingBatchService).generateMissingEmbeddings();
        inOrder.verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 1(사기업 수집) 실패 시에도 Step 2~4가 실행된다")
    void runDailyPipeline_step1실패_나머지실행() {
        doThrow(new RuntimeException("수집 실패"))
                .when(privateJobBatchCollectService).collectAll();

        scheduler.runDailyPipeline();

        verify(jobDataSyncService).syncPublicJobOpenings();
        verify(embeddingBatchService).generateMissingEmbeddings();
        verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 2(공기업 수집) 실패 시에도 Step 3~4가 실행된다")
    void runDailyPipeline_step2실패_나머지실행() {
        doThrow(new RuntimeException("공기업 수집 실패"))
                .when(jobDataSyncService).syncPublicJobOpenings();

        scheduler.runDailyPipeline();

        verify(privateJobBatchCollectService).collectAll();
        verify(embeddingBatchService).generateMissingEmbeddings();
        verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 3(임베딩) 실패 시에도 Step 4가 실행된다")
    void runDailyPipeline_step3실패_나머지실행() {
        doThrow(new RuntimeException("임베딩 실패"))
                .when(embeddingBatchService).generateMissingEmbeddings();

        scheduler.runDailyPipeline();

        verify(privateJobBatchCollectService).collectAll();
        verify(jobDataSyncService).syncPublicJobOpenings();
        verify(privateMatchBatchService).scoreNewAndUpdatedPostings();
    }

    @Test
    @DisplayName("Step 4(점수 산출) 실패 시에도 예외 전파 없이 정상 종료된다")
    void runDailyPipeline_step4실패_정상종료() {
        doThrow(new RuntimeException("점수 산출 실패"))
                .when(privateMatchBatchService).scoreNewAndUpdatedPostings();

        scheduler.runDailyPipeline();

        verify(privateJobBatchCollectService).collectAll();
        verify(jobDataSyncService).syncPublicJobOpenings();
        verify(embeddingBatchService).generateMissingEmbeddings();
    }
}

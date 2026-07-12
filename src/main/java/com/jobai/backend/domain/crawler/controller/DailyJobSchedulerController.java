package com.jobai.backend.domain.crawler.controller;

import com.jobai.backend.domain.crawler.scheduler.DailyJobScheduler;
import com.jobai.backend.domain.crawler.service.PrivateJobPostingService;
import com.jobai.backend.domain.home.service.PrivateMatchBatchService;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import com.jobai.backend.domain.techcard.service.TechCardCollectService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 새벽 파이프라인 수동 트리거용 API.
 */
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
public class DailyJobSchedulerController implements DailyJobSchedulerControllerDocs {

    private final DailyJobScheduler dailyJobScheduler;
    private final PrivateJobPostingService privateJobPostingService;
    private final EmbeddingBatchService embeddingBatchService;
    private final PrivateMatchBatchService privateMatchBatchService;
    private final TechCardCollectService techCardCollectService;

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
}

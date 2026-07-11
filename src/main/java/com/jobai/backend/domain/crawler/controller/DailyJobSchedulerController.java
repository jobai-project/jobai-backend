package com.jobai.backend.domain.crawler.controller;

import com.jobai.backend.domain.crawler.scheduler.DailyJobScheduler;
import com.jobai.backend.domain.crawler.service.PrivateJobPostingService;
import com.jobai.backend.domain.home.service.PrivateMatchBatchService;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.search.service.EmbeddingBatchService;
import com.jobai.backend.domain.search.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 새벽 파이프라인 수동 트리거용 API.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
public class DailyJobSchedulerController implements DailyJobSchedulerControllerDocs {

    private final DailyJobScheduler dailyJobScheduler;
    private final PrivateJobPostingService privateJobPostingService;
    private final EmbeddingBatchService embeddingBatchService;
    private final PrivateMatchBatchService privateMatchBatchService;
    private final ResumesRepository resumesRepository;
    private final EmbeddingService embeddingService;

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
        List<Resumes> targets = resumesRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .filter(r -> r.getExtractedText() != null && !r.getExtractedText().isBlank())
                .filter(r -> r.getEmbedding() == null)
                .toList();

        if (targets.isEmpty()) {
            return ResponseEntity.ok("임베딩 대상 이력서가 없습니다");
        }

        int success = 0;
        for (Resumes resume : targets) {
            try {
                float[] vector = embeddingService.embedResumeText(resume.getExtractedText());
                resume.updateEmbedding(vector);
                resumesRepository.save(resume);
                success++;
            } catch (Exception e) {
                log.warn("이력서 임베딩 실패: resumeId={}, error={}", resume.getId(), e.getMessage());
            }
        }
        return ResponseEntity.ok("이력서 임베딩 완료: " + success + "/" + targets.size() + "건 성공");
    }
}

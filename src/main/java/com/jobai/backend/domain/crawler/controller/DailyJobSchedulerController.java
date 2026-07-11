package com.jobai.backend.domain.crawler.controller;

import com.jobai.backend.domain.crawler.scheduler.DailyJobScheduler;
import com.jobai.backend.domain.crawler.service.PrivateJobPostingService;
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
}

package com.jobai.backend.domain.crawler.controller;

import com.jobai.backend.domain.crawler.scheduler.DailyJobScheduler;
import com.jobai.backend.domain.crawler.service.PrivateJobPostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 새벽 파이프라인 수동 트리거용 API.
 * local 프로필에서만 활성화된다.
 */
@Tag(name = "Scheduler", description = "새벽 파이프라인 수동 실행")
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
@Profile("local")
public class DailyJobSchedulerController {

    private final DailyJobScheduler dailyJobScheduler;
    private final PrivateJobPostingService privateJobPostingService;

    @Operation(summary = "새벽 파이프라인 수동 실행",
            description = "사기업 수집 → 공기업 수집 → 임베딩 생성 → 매칭 점수 산출 파이프라인을 즉시 실행한다.")
    @PostMapping("/daily-pipeline")
    public ResponseEntity<String> triggerDailyPipeline() {
        dailyJobScheduler.runDailyPipeline();
        return ResponseEntity.ok("새벽 파이프라인 실행 완료");
    }

    @Operation(summary = "미분류 공고 일괄 분류",
            description = "jobCategory가 null인 공고를 LLM으로 일괄 분류한다.")
    @PostMapping("/classify")
    public ResponseEntity<String> classifyUnclassified() {
        int total = privateJobPostingService.classifyUnclassified(100);
        return ResponseEntity.ok("미분류 공고 " + total + "건 분류 완료");
    }

    @Operation(summary = "고용형태/경력 미분류 공고 일괄 분류",
            description = "jobCategory는 있지만 employmentType 또는 experienceLevel이 null인 공고를 LLM으로 일괄 분류한다.")
    @PostMapping("/classify-employment")
    public ResponseEntity<String> classifyMissingEmploymentTypes() {
        int total = privateJobPostingService.classifyMissingEmploymentTypes(100);
        return ResponseEntity.ok("고용형태/경력 미분류 공고 " + total + "건 분류 완료");
    }
}

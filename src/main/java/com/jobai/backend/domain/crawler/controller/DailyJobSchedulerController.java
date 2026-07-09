package com.jobai.backend.domain.crawler.controller;

import com.jobai.backend.domain.crawler.scheduler.DailyJobScheduler;
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

    @Operation(summary = "새벽 파이프라인 수동 실행",
            description = "사기업 수집 → 공기업 수집 → 임베딩 생성 → 매칭 점수 산출 파이프라인을 즉시 실행한다.")
    @PostMapping("/daily-pipeline")
    public ResponseEntity<String> triggerDailyPipeline() {
        dailyJobScheduler.runDailyPipeline();
        return ResponseEntity.ok("새벽 파이프라인 실행 완료");
    }
}

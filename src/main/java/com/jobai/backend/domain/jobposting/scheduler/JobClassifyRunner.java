package com.jobai.backend.domain.jobposting.scheduler;

import com.jobai.backend.domain.jobposting.service.PrivateJobPostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 기존 미분류 공고 일괄 분류 실행기. "classify" 프로파일일 때만 동작.
 *
 * 실행: .\gradlew bootRun --args="--spring.profiles.active=local,classify"
 */
@Slf4j
@Component
@Profile("classify")
@RequiredArgsConstructor
public class JobClassifyRunner implements ApplicationRunner {

    private static final int BATCH_PAGE_SIZE = 100;

    private final PrivateJobPostingService service;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[분류] 미분류 공고 일괄 분류 시작");
        int total = service.classifyUnclassified(BATCH_PAGE_SIZE);
        log.info("[분류] 완료 — 총 {}건 분류", total);
    }
}

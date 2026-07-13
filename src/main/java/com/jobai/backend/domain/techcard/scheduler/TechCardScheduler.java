package com.jobai.backend.domain.techcard.scheduler;

import com.jobai.backend.domain.techcard.service.TechCardCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.techcard.enabled", havingValue = "true", matchIfMissing = true)
public class TechCardScheduler {

    private final TechCardCollectService techCardCollectService;

    @Scheduled(cron = "${scheduler.techcard.cron:0 0 7 * * *}", zone = "Asia/Seoul")
    public void collectTechCards() {
        log.info("[TechCardScheduler] ===== IT 뉴스 카드 수집 시작 =====");
        long start = System.currentTimeMillis();

        try {
            techCardCollectService.collectAndSummarize();
        } catch (Exception e) {
            log.error("[TechCardScheduler] 수집 실패: {}", e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[TechCardScheduler] ===== IT 뉴스 카드 수집 종료 ({}ms) =====", elapsed);
    }
}

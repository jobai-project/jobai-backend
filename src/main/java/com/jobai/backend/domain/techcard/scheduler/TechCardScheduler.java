package com.jobai.backend.domain.techcard.scheduler;

import com.jobai.backend.domain.techcard.service.TechCardCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IT 뉴스 카드 자동 수집 스케줄러.
 * <p>매일 오전 4시(KST)에 외부 소스에서 기사를 수집하고 LLM 요약을 수행한다.
 * {@code scheduler.techcard.enabled=false}로 비활성화할 수 있다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.techcard.enabled", havingValue = "true", matchIfMissing = true)
public class TechCardScheduler {

    private final TechCardCollectService techCardCollectService;

    @Scheduled(cron = "${scheduler.techcard.cron:0 0 4 * * *}", zone = "Asia/Seoul")
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

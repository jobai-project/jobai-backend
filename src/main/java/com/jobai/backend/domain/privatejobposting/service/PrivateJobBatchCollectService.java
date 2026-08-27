package com.jobai.backend.domain.privatejobposting.service;

import com.jobai.backend.domain.privatejobposting.scheduler.DailyJobScheduler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 모든 사기업 YAML 스펙을 순회하며 일괄 수집하는 배치 서비스.
 * 새벽 스케줄러({@link DailyJobScheduler})에서 호출된다.
 */
@Slf4j
@Service
public class PrivateJobBatchCollectService {

    private final PrivateJobCollectService collectService;
    private final Counter crawlSuccessCounter;
    private final Counter crawlFailureCounter;

    private static final String SPECS_PATTERN = "classpath:specs/*.yaml";

    public PrivateJobBatchCollectService(PrivateJobCollectService collectService,
                                        MeterRegistry meterRegistry) {
        this.collectService = collectService;
        this.crawlSuccessCounter = Counter.builder("crawl.collect.success")
                .description("크롤링 수집 성공 건수")
                .register(meterRegistry);
        this.crawlFailureCounter = Counter.builder("crawl.collect.failure")
                .description("크롤링 수집 실패 건수")
                .register(meterRegistry);
    }

    @Value("${scheduler.daily.exclude-companies:testco}")
    private Set<String> excludeCompanies;

    /**
     * resources/specs/ 디렉토리의 모든 YAML 스펙을 스캔하여 회사별로 수집한다.
     * 한 회사가 실패해도 나머지는 계속 진행한다.
     */
    /** @return 신규 수집된 공고 건수 */
    public int collectAll() {
        List<String> companies = discoverCompanies();
        if (companies.isEmpty()) {
            log.warn("[배치수집] specs 디렉토리에 수집할 회사가 없습니다");
            return 0;
        }

        log.info("[배치수집] 시작 — 대상 회사 {}개: {}", companies.size(), companies);

        int totalInserted = 0;
        int totalUpdated = 0;
        int totalClosed = 0;
        int successCount = 0;
        int failCount = 0;

        for (String company : companies) {
            try {
                SaveResult result = collectService.collectAndSave(company);
                totalInserted += result.getInsertedCount();
                totalUpdated += result.getUpdatedCount();
                totalClosed += result.getClosedCount();
                successCount++;
                crawlSuccessCounter.increment();
                log.info("[배치수집] [{}] 완료 — 신규 {}, 변경 {}, 마감 {}",
                        company, result.getInsertedCount(), result.getUpdatedCount(), result.getClosedCount());
            } catch (Exception e) {
                failCount++;
                crawlFailureCounter.increment();
                log.error("[배치수집] [{}] 실패: {}", company, e.getMessage(), e);
            }
        }

        log.info("[배치수집] 완료 — 회사 성공 {}/실패 {}, 전체 신규 {}, 변경 {}, 마감 {}",
                successCount, failCount, totalInserted, totalUpdated, totalClosed);
        return totalInserted;
    }

    /**
     * classpath:specs/*.yaml 에서 회사명(파일명)을 추출한다.
     * {@code scheduler.daily.exclude-companies} 프로퍼티에 지정된 회사는 제외한다.
     */
    private List<String> discoverCompanies() {
        List<String> companies = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(SPECS_PATTERN);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                String company = filename.replace(".yaml", "");
                if (excludeCompanies.contains(company)) continue;
                companies.add(company);
            }
        } catch (IOException e) {
            log.error("[배치수집] specs 디렉토리 스캔 실패", e);
        }
        return companies;
    }
}

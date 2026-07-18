package com.jobai.backend.domain.jobposting.runner;

import com.jobai.backend.domain.jobposting.service.PrivateJobCollectService;
import com.jobai.backend.domain.jobposting.service.SaveResult;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 수동 수집 실행기. 평소엔 비활성이고 "collect" 프로파일일 때만 동작한다.
 *
 * <p>실행 예 (PowerShell):
 * <pre>
 *   .\gradlew bootRun --args="--spring.profiles.active=local,collect --collect.company=doodlin"
 * </pre>
 *
 * <p>--collect.company 인자로 회사를 지정한다. 여러 회사는 콤마로 구분(doodlin,upstage).
 * 지정이 없으면 아무것도 하지 않고 안내만 남긴다.
 */
@Slf4j
@Component
@Profile("collect")
@RequiredArgsConstructor
public class PrivateJobCollectRunner implements ApplicationRunner {

    private final PrivateJobCollectService collectService;

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = args.getOptionValues("collect.company");
        if (values == null || values.isEmpty()) {
            log.warn("수집할 회사를 지정하세요: --collect.company=doodlin (콤마로 여러 개 가능)");
            return;
        }

        // --collect.company=doodlin,upstage 또는 인자 반복 모두 지원
        for (String value : values) {
            for (String company : value.split(",")) {
                String name = company.trim();
                if (name.isEmpty()) continue;
                try {
                    SaveResult result = collectService.collectAndSave(name);
                    log.info("[{}] 완료 — 신규 {}, 변경 {}, 마감 {}",
                            name, result.getInsertedCount(), result.getUpdatedCount(),
                            result.getClosedCount());
                } catch (Exception e) {
                    // 한 회사 실패가 다른 회사 수집을 막지 않도록 격리
                    log.error("[{}] 수집 실패: {}", name, e.getMessage(), e);
                }
            }
        }
    }
}

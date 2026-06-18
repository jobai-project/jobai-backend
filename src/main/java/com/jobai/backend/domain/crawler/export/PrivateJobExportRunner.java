package com.jobai.backend.domain.crawler.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사기업 공고(private_job_postings)를 AI 매칭 파트용 JSON 파일로 export 한다.
 *
 * <p>흐름: 사기업 공고 조회 → 마감 제외 → IT 직군 필터 → HTML 본문 정제 → JSON 파일.
 * 사기업/공기업은 테이블이 분리(private_job_postings vs job_postings)되어 있으므로
 * 이 테이블만 읽으면 자동으로 사기업만 포함된다.
 *
 * <p>출력 필드는 AI 매칭(이력서↔공고 적합도 계산)에 필요한 것만 담는다:
 * source_job_id(식별자), title, description(매칭 신호).
 * company·location·apply_url·job_category 등은 제외한다.
 * (job_category 는 현재 수집 단계에서 채워지지 않아 제외; 추후 수집 보강 시 추가 가능)
 *
 * <p><b>DB 원본은 보존</b>한다. 정제는 export 시점에만 수행하며 entity 의 description 은
 * 수정하지 않는다(읽기 전용 조회 → 메모리상에서만 가공).
 *
 * <p>{@code @Profile("export")} 로 분리해, 평소 앱 실행에는 영향을 주지 않는다.
 * 실행: {@code gradlew bootRun --args='--spring.profiles.active=export'}
 */
@Component
@Profile("export")
public class PrivateJobExportRunner implements ApplicationRunner {

    private static final String OUTPUT_FILE = "ai_jobs_export.json";

    private final PrivateJobPostingRepository repository;
    private final JobDescriptionCleanser cleanser;
    private final ObjectMapper objectMapper;

    public PrivateJobExportRunner(PrivateJobPostingRepository repository,
                                  JobDescriptionCleanser cleanser,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.cleanser = cleanser;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<PrivateJobPosting> all = repository.findAll();

        List<Map<String, Object>> exported = all.stream()
                .filter(j -> !j.isClosed())                                  // 마감 안 된 공고만
                .filter(j -> ItJobFilter.isItJob(j.getTitle(), j.getJobCategory()))  // IT 직군만
                .map(this::toExportMap)                                      // 정제 + 매핑
                .toList();

        Path path = Paths.get(OUTPUT_FILE);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), exported);

        System.out.printf("[export] 사기업 IT 공고 %d건 → %s%n",
                exported.size(), path.toAbsolutePath());
    }

    /** 엔티티 → export용 Map (AI 매칭 입력: 식별자 + 매칭 신호 필드). */
    private Map<String, Object> toExportMap(PrivateJobPosting j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_job_id", j.getSourceJobId());   // 식별자 (매칭 결과 → 실제 공고 되짚기)
        m.put("title", j.getTitle());                 // 매칭 신호
        m.put("description", cleanser.clean(j.getDescription()));  // 매칭 신호 (본문, HTML 정제)
        return m;
    }
}
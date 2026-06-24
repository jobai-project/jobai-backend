package com.jobai.backend.domain.crawler.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.classify.JobCategory;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
 * <p>데이터가 커져도 안전하도록 전량 적재(findAll) 대신 <b>페이지 단위(Page)</b>로
 * 끊어 읽어 누적한다. 한 페이지씩 처리하므로 메모리 사용이 일정하게 유지된다.
 *
 * <p>{@code @Profile("export")} 로 분리해, 평소 앱 실행에는 영향을 주지 않는다.
 * 실행: {@code gradlew bootRun --args='--spring.profiles.active=local,export'}
 */
@Component
@Profile("export")
public class PrivateJobExportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrivateJobExportRunner.class);
    private static final String OUTPUT_FILE = "ai_jobs_export.json";
    private static final int PAGE_SIZE = 500;   // 한 번에 읽을 공고 수

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
        List<Map<String, Object>> exported = new ArrayList<>();

        // id 정렬로 페이지를 끊어 읽는다(정렬 없으면 페이지 경계가 불안정).
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("id").ascending());
        Page<PrivateJobPosting> page;
        int scanned = 0;

        do {
            page = repository.findAll(pageable);
            for (PrivateJobPosting j : page.getContent()) {
                scanned++;
                if (j.isClosed()) {
                    continue;                                   // 마감 공고 제외
                }
                JobCategory category = JobCategory.fromLabel(j.getJobCategory());
                if (!category.isMatchTarget()) {
                    continue;                                   // 비대상·미분류 제외
                }
                exported.add(toExportMap(j, category));                   // 정제 + 매핑
            }
            pageable = page.nextPageable();
        } while (page.hasNext());

        Path path = Paths.get(OUTPUT_FILE);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), exported);

        log.info("[export] 전체 {}건 중 사기업 IT 공고 {}건 → {}",
                scanned, exported.size(), path.toAbsolutePath());
    }

    /** 엔티티 → export용 Map (AI 매칭 입력: 식별자 + 매칭 신호 필드). */
    private Map<String, Object> toExportMap(PrivateJobPosting j, JobCategory category) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_job_id", j.getSourceJobId());
        m.put("title", j.getTitle());
        m.put("job_category", category.getLabel());   // 원본 대신 정규화된 라벨
        m.put("description", cleanser.clean(j.getDescription()));
        return m;
    }
}
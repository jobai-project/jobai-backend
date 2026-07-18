package com.jobai.backend.domain.privatejobposting.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.enums.JobCategory;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.privatejobposting.service.JobDescriptionCleanser;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사기업 공고 전체 컬럼 export. 미분류·비대상 카테고리는 제외.
 *
 * <p>실행: {@code gradlew bootRun --args='--spring.profiles.active=local,export --export.mode=full'}
 * 신규만: {@code gradlew bootRun --args='--spring.profiles.active=local,export --export.mode=full --export.since=2026-06-29'}
 */
@Component
@Profile("export")
public class
PrivateJobFullExportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PrivateJobFullExportRunner.class);
    private static final String OUTPUT_FILE = "ai_jobs_full_export.json";
    private static final int PAGE_SIZE = 500;

    private final PrivateJobPostingRepository repository;
    private final JobDescriptionCleanser cleanser;
    private final ObjectMapper objectMapper;

    public PrivateJobFullExportRunner(PrivateJobPostingRepository repository,
                                      JobDescriptionCleanser cleanser,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.cleanser = cleanser;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // --export.mode=full 이 없으면 실행하지 않음 (기존 Runner와 공존)
        List<String> modeValues = args.getOptionValues("export.mode");
        if (modeValues == null || modeValues.stream().noneMatch("full"::equalsIgnoreCase)) {
            return;
        }

        LocalDateTime since = parseSinceOption(args);

        List<Map<String, Object>> exported = new ArrayList<>();

        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("id").ascending());
        Page<PrivateJobPosting> page;
        int scanned = 0;

        do {
            page = (since != null)
                    ? repository.findByCreatedAtAfter(since, pageable)
                    : repository.findAll(pageable);

            for (PrivateJobPosting j : page.getContent()) {
                scanned++;
                if (j.isClosed()) {
                    continue;
                }
                JobCategory category = JobCategory.fromLabel(j.getJobCategory());
                if (!category.isMatchTarget()) {
                    continue;  // 미분류·비대상 제외
                }
                exported.add(toFullMap(j, category));
            }
            pageable = page.nextPageable();
        } while (page.hasNext());

        String outputFile = (since != null) ? "ai_jobs_full_export_new.json" : OUTPUT_FILE;
        Path path = Paths.get(outputFile);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), exported);

        if (since != null) {
            log.info("[full-export] {} 이후 신규 {}건 중 IT 공고 {}건 → {}",
                    since.toLocalDate(), scanned, exported.size(), path.toAbsolutePath());
        } else {
            log.info("[full-export] 전체 {}건 중 IT 공고 {}건 → {}",
                    scanned, exported.size(), path.toAbsolutePath());
        }
    }

    private LocalDateTime parseSinceOption(ApplicationArguments args) {
        List<String> values = args.getOptionValues("export.since");
        if (values == null || values.isEmpty()) {
            return null;
        }
        return LocalDate.parse(values.get(0).trim()).atStartOfDay();
    }

    /** 엔티티 → 전체 컬럼 Map. */
    private Map<String, Object> toFullMap(PrivateJobPosting j, JobCategory category) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("company", j.getCompany());
        m.put("source_job_id", j.getSourceJobId());
        m.put("title", j.getTitle());
        m.put("location", j.getLocation());
        m.put("employment_type", j.getEmploymentType());
        m.put("job_category", category.getLabel());
        m.put("description", cleanser.clean(j.getDescription()));
        m.put("apply_url", j.getApplyUrl());
        m.put("deadline", j.getDeadline() != null ? j.getDeadline().toString() : null);
        m.put("is_closed", j.isClosed());
        m.put("last_seen_at", j.getLastSeenAt() != null ? j.getLastSeenAt().toString() : null);
        m.put("created_at", j.getCreatedAt() != null ? j.getCreatedAt().toString() : null);
        m.put("updated_at", j.getUpdatedAt() != null ? j.getUpdatedAt().toString() : null);
        return m;
    }
}

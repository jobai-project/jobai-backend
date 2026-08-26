package com.jobai.backend.domain.privatejob.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.privatejob.entity.JobPostingSummary;
import com.jobai.backend.domain.privatejob.repository.JobPostingSummaryRepository;
import com.jobai.backend.domain.privatejob.service.PrivateJobDetailService;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Component
@Profile("summarize")
@RequiredArgsConstructor
public class JobSummaryRunner implements ApplicationRunner {

    private final PrivateJobPostingRepository jobPostingRepository;
    private final PrivateJobDetailService detailService;
    private final JobPostingSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[요약] 백엔드 카테고리 공고 일괄 요약 시작");

        List<PrivateJobPosting> postings = jobPostingRepository
                .findActiveByValidCategories(List.of("백엔드"));

        log.info("[요약] 대상 공고 수: {}건", postings.size());

        int success = 0;
        int skipped = 0;
        int failed = 0;

        for (PrivateJobPosting posting : postings) {
            try {
                detailService.getSummary(posting.getId());
                success++;
                if (success % 10 == 0) {
                    log.info("[요약] 진행 중… {}건 완료", success);
                }
            } catch (Exception e) {
                failed++;
                log.warn("[요약] 공고 ID={} 실패: {}", posting.getId(), e.getMessage());
            }
        }

        log.info("[요약] 완료 — 성공 {}, 실패 {}, 총 {}건", success, failed, postings.size());

        // 기술스택 집계
        aggregateTechStacks(postings);
    }

    private void aggregateTechStacks(List<PrivateJobPosting> postings) {
        Map<String, Integer> techCount = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (PrivateJobPosting posting : postings) {
            summaryRepository.findByJobPostingId(posting.getId()).ifPresent(summary -> {
                try {
                    JsonNode root = objectMapper.readTree(summary.getSummaryJson());
                    JsonNode techStack = root.get("techStack");
                    if (techStack != null && techStack.isArray()) {
                        for (JsonNode tech : techStack) {
                            String name = tech.asText().trim();
                            if (!name.isEmpty()) {
                                techCount.merge(name, 1, Integer::sum);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("[요약] 기술스택 파싱 실패 (jobPostingId={}): {}", posting.getId(), e.getMessage());
                }
            });
        }

        // 건수 내림차순 정렬
        List<Map.Entry<String, Integer>> sorted = techCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        log.info("[요약] ===== 기술스택 집계 결과 =====");
        for (Map.Entry<String, Integer> entry : sorted) {
            log.info("[요약]  {} : {}건", entry.getKey(), entry.getValue());
        }
        log.info("[요약] ===== 총 {} 종류 =====", sorted.size());
    }
}

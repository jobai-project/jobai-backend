package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion(RRF)으로 키워드 검색 결과와 벡터 검색 결과를 병합한다.
 *
 * <p>RRF 공식: {@code score(d) = Σ 1/(k + rank)}
 * <ul>
 *   <li>k = 60 (표준 상수, 상위 문서의 과도한 지배를 방지)</li>
 *   <li>rank는 1-indexed (1등 = rank 1)</li>
 *   <li>양쪽 리스트에 모두 나타난 문서는 점수 합산</li>
 * </ul>
 */
@Slf4j
@Component
public class HybridSearchMerger {

    private static final int RRF_K = 60;

    /**
     * 두 검색 결과를 RRF로 병합한다. 페이지네이션은 적용하지 않는다.
     *
     * @param keywordResults 키워드 검색 결과 (이미 정렬된 상태)
     * @param vectorResults  벡터 검색 결과 (이미 정렬된 상태)
     * @return RRF 점수 내림차순 정렬된 전체 병합 결과
     */
    public List<JobSummary> merge(List<JobSummary> keywordResults,
                                   List<JobSummary> vectorResults) {
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, JobSummary> jobMap = new HashMap<>();

        addScores(rrfScores, jobMap, keywordResults);
        addScores(rrfScores, jobMap, vectorResults);

        log.info("[RRF] 키워드 결과={}, 벡터 결과={}, 병합 후 고유 문서={}",
                keywordResults.size(), vectorResults.size(), rrfScores.size());

        List<JobSummary> merged = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    JobSummary job = jobMap.get(e.getKey());
                    log.debug("[RRF] {} | {} | score={}", e.getKey(), job.title(),
                            String.format("%.6f", e.getValue()));
                    return job;
                })
                .toList();

        if (!merged.isEmpty()) {
            merged.stream().limit(5).forEach(job -> {
                String key = compositeKey(job);
                log.info("[RRF] rank: {} | {} | score={}", key, job.title(),
                        String.format("%.6f", rrfScores.get(key)));
            });
        }

        return merged;
    }

    private void addScores(Map<String, Double> rrfScores,
                            Map<String, JobSummary> jobMap,
                            List<JobSummary> results) {
        for (int rank = 0; rank < results.size(); rank++) {
            JobSummary job = results.get(rank);
            String key = compositeKey(job);
            double score = 1.0 / (RRF_K + rank + 1);
            rrfScores.merge(key, score, Double::sum);
            jobMap.putIfAbsent(key, job);
        }
    }

    private String compositeKey(JobSummary job) {
        return job.source() + ":" + job.id();
    }
}

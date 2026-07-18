package com.jobai.backend.domain.search.service;

import com.jobai.backend.global.ai.client.AiRerankClient;
import com.jobai.backend.global.ai.dto.RerankRequest;
import com.jobai.backend.global.ai.dto.RerankRequest.RerankCandidate;
import com.jobai.backend.global.ai.dto.RerankResponse;
import com.jobai.backend.global.ai.dto.RerankResponse.RerankScore;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cross-Encoder 기반 검색 결과 재정렬기.
 * ai-server의 {@code POST /rerank} 엔드포인트를 호출하여
 * 상위 후보군을 쿼리 의도에 맞게 재정렬한다.
 *
 * <p>비활성화되거나 ai-server 호출이 실패하면 입력 순서를 그대로 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchReranker {

    private final AiRerankClient aiRerankClient;

    @Value("${search.rerank.enabled:false}")
    private boolean enabled;

    @Value("${search.rerank.top-n:50}")
    private int topN;

    /**
     * 후보 목록을 재정렬한다. 상위 top-n 건만 재정렬하고 나머지는 뒤에 붙인다.
     *
     * @param originalQuery 원본 검색 쿼리
     * @param candidates    재정렬 대상 목록
     * @return 재정렬된 목록 (실패 시 원본 그대로)
     */
    public List<JobSummary> rerank(String originalQuery, List<JobSummary> candidates) {
        if (!enabled || candidates.isEmpty()) {
            return candidates;
        }

        try {
            List<JobSummary> toRerank = candidates.subList(0, Math.min(topN, candidates.size()));
            List<JobSummary> remainder = candidates.size() > topN
                    ? candidates.subList(topN, candidates.size())
                    : List.of();

            RerankRequest request = buildRequest(originalQuery, toRerank);
            RerankResponse response = aiRerankClient.rerank(request).block();

            if (response == null || response.results() == null || response.results().isEmpty()) {
                log.warn("[리랭킹] 응답이 비어있음, 기존 순서 유지");
                return candidates;
            }

            List<JobSummary> reranked = reorderByScore(toRerank, response);

            log.info("[리랭킹] 성공: query={}, 후보={}건, 상위 3개={}", originalQuery, toRerank.size(),
                    reranked.stream().limit(3)
                            .map(j -> j.source() + ":" + j.id() + " " + j.title())
                            .toList());

            List<JobSummary> result = new ArrayList<>(reranked);
            result.addAll(remainder);
            return result;
        } catch (Exception e) {
            log.warn("[리랭킹] 실패, 기존 순서 유지: query={}, error={}", originalQuery, e.getMessage());
            return candidates;
        }
    }

    private RerankRequest buildRequest(String query, List<JobSummary> candidates) {
        List<RerankCandidate> rerankCandidates = candidates.stream()
                .map(j -> new RerankCandidate(
                        j.id(), j.source(), j.title(), j.company(), j.jobCategory()))
                .toList();
        return new RerankRequest(query, rerankCandidates);
    }

    private List<JobSummary> reorderByScore(List<JobSummary> original, RerankResponse response) {
        Map<String, Double> scoreMap = response.results().stream()
                .collect(Collectors.toMap(
                        r -> r.source() + ":" + r.id(),
                        RerankScore::score,
                        (a, b) -> a));

        return original.stream()
                .sorted(Comparator.comparingDouble(
                        (JobSummary j) -> scoreMap.getOrDefault(j.source() + ":" + j.id(), 0.0))
                        .reversed())
                .toList();
    }
}

package com.jobai.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * ai-server {@code POST /rerank} 요청 DTO.
 *
 * @param query      원본 검색 쿼리
 * @param candidates 재정렬 대상 공고 목록
 */
public record RerankRequest(
        String query,
        List<RerankCandidate> candidates
) {
    public record RerankCandidate(
            long id,
            String source,
            String title,
            String company,
            @JsonProperty("job_category") String jobCategory
    ) {}
}

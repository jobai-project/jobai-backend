package com.jobai.backend.global.ai.dto;

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
    /**
     * 재정렬 대상 공고 후보.
     *
     * @param id          공고 ID
     * @param source      출처 (PRIVATE / PUBLIC)
     * @param title       공고 제목
     * @param company     회사명
     * @param jobCategory 직무 카테고리
     */
    public record RerankCandidate(
            long id,
            String source,
            String title,
            String company,
            @JsonProperty("job_category") String jobCategory
    ) {}
}

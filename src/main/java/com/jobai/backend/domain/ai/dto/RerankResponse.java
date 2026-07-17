package com.jobai.backend.domain.ai.dto;

import java.util.List;

/**
 * ai-server {@code POST /rerank} 응답 DTO.
 *
 * @param results 재정렬된 결과 (score 내림차순)
 */
public record RerankResponse(
        List<RerankScore> results
) {
    /**
     * 개별 재정렬 점수.
     *
     * @param id     공고 ID
     * @param source 출처 (PRIVATE / PUBLIC)
     * @param score  Cross-Encoder 관련도 점수 (높을수록 쿼리와 관련성 높음)
     */
    public record RerankScore(
            long id,
            String source,
            double score
    ) {}
}

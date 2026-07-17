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
    public record RerankScore(
            long id,
            String source,
            double score
    ) {}
}

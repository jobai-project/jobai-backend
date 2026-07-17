package com.jobai.backend.domain.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ScrapRankingResponse(
        @Schema(description = "스크랩 순위 목록. 최대 5개까지 반환합니다.")
        List<ScrapRankingItem> rankings
) {
    public record ScrapRankingItem(
            @Schema(description = "순위", example = "1")
            int rank,
            @Schema(description = "공고 출처. PUBLIC=공기업, PRIVATE=사기업", example = "PRIVATE")
            String source,
            @Schema(description = "공고 고유 ID", example = "55")
            Long sourceId,
            @Schema(description = "공고 제목", example = "Java 백엔드 개발자")
            String title,
            @Schema(description = "회사명", example = "카카오페이")
            String companyName,
            @Schema(description = "전체 사용자 스크랩 수", example = "18")
            long scrapCount
    ) {
    }
}

package com.jobai.backend.domain.techcard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "IT 인사이트 카드 응답")
public record TechCardResponse(
        @Schema(description = "카드 목록 (최대 내부 카드 2장 + 외부 카드 1장)")
        List<CardItem> cards
) {
    @Schema(description = "인사이트 카드 항목")
    public record CardItem(
            @Schema(description = "카드 ID (내부 카드는 null)", example = "42")
            Long id,
            @Schema(description = "출처 (INTERNAL, HACKERNEWS)", example = "INTERNAL")
            String source,
            @Schema(description = "카드 배지 텍스트", example = "요즘 뜨는 트렌드")
            String badge,
            @Schema(description = "헤드라인", example = "오늘 백엔드 공고 5건이 새로 올라왔어요")
            String headline,
            @Schema(description = "부연설명", example = "프론트엔드 3건, AI/ML 2건도 함께 수집됐어요")
            String subtext,
            @Schema(description = "원문 링크 (내부 카드는 null)")
            String originalUrl,
            @Schema(description = "원문 발행 시각")
            LocalDateTime publishedAt,
            @Schema(description = "카드 생성 시각")
            LocalDateTime createdAt,
            @Schema(description = "관련 공고 목록 (신규 공고 카드에서만 사용)")
            List<RelatedJob> relatedJobs
    ) {}

    @Schema(description = "관련 공고 항목")
    public record RelatedJob(
            @Schema(description = "공고 ID", example = "123")
            Long id,
            @Schema(description = "기업 유형 (PRIVATE, PUBLIC)", example = "PRIVATE")
            String source,
            @Schema(description = "회사명", example = "카카오")
            String companyName,
            @Schema(description = "공고 제목", example = "백엔드 개발자")
            String title
    ) {}
}

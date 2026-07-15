package com.jobai.backend.domain.techcard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "IT 인사이트 카드 응답")
public record TechCardResponse(
        @Schema(description = "카드 목록 (최대 신규 공고 1장 + 테크 뉴스 2장)")
        List<CardItem> cards
) {
    @Schema(description = "인사이트 카드 항목")
    public record CardItem(
            @Schema(description = "카드 ID (내부 카드는 null)", example = "42")
            Long id,
            @Schema(description = "출처 (INTERNAL, HACKERNEWS, GEEKNEWS)", example = "INTERNAL")
            String source,
            @Schema(description = "카드 배지 텍스트", example = "테크 뉴스")
            String badge,
            @Schema(description = "헤드라인", example = "구글, AI 코딩 도우미 무료로 풀었어요")
            String headline,
            @Schema(description = "부연설명", example = "VS Code에서 바로 쓸 수 있어요")
            String subtext,
            @Schema(description = "원문 링크 (내부 카드는 null)")
            String originalUrl,
            @Schema(description = "원문 발행 시각")
            LocalDateTime publishedAt,
            @Schema(description = "카드 생성 시각")
            LocalDateTime createdAt,
            @Schema(description = "관련 공고 목록")
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
            String title,
            @Schema(description = "AI 매칭 점수 (비로그인/이력서 미업로드 시 null)", example = "85", nullable = true)
            Integer matchScore,
            @Schema(description = "마감일", example = "2026-08-01", nullable = true)
            LocalDate deadline,
            @Schema(description = "고용형태", example = "정규직", nullable = true)
            String employmentType
    ) {}
}

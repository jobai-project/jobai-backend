package com.jobai.backend.domain.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record JobSearchResponse(
        @Schema(description = "검색 조건에 맞는 전체 공고 수 (현재 페이지 크기와 무관)", example = "2")
        long totalCount,
        @Schema(description = "검색 결과 공고 목록 (요청한 size만큼, 없으면 빈 배열)")
        List<JobSummary> jobs,
        @Schema(description = "검색 방식/추출 정보 (디버깅·튜닝용, 화면에 꼭 표시할 필요는 없음)")
        SearchInfo searchInfo
) {

    public record JobSummary(
            @Schema(description = "공고 고유 ID", example = "55")
            Long id,
            @Schema(description = "공고 출처. 값: PUBLIC(공기업), PRIVATE(사기업)", example = "PRIVATE")
            String source,
            @Schema(description = "공고 제목", example = "백엔드 개발자")
            String title,
            @Schema(description = "회사명/기관명", example = "카카오")
            String company,
            @Schema(description = "근무지역", example = "판교")
            String location,
            @Schema(description = "직무 분류 (사기업만 제공, 공기업은 null)", example = "백엔드", nullable = true)
            String jobCategory,
            @Schema(description = "고용형태", example = "정규직", nullable = true)
            String employmentType,
            @Schema(description = "경력 구분", example = "신입", nullable = true)
            String experienceLevel,
            @Schema(description = "지원 링크", example = "https://careers.kakao.com/jobs/P-14472")
            String applyUrl,
            @Schema(description = "마감일 (없으면 null)", example = "2026-07-16", nullable = true)
            LocalDate deadline,
            @Schema(description = "공고 등록/수집 일시")
            LocalDateTime createdAt,
            @Schema(description = "매칭 유형. EXACT=모든 필터 충족, SIMILAR=일부 필터 미충족(null 포함)", example = "EXACT")
            String matchType
    ) {}

    public record SearchInfo(
            @Schema(description = "검색 방식. 값: KEYWORD(구조화 검색), VECTOR(의미 기반 벡터 검색)", example = "KEYWORD")
            String method,
            @Schema(description = "쿼리에서 인식된 직무 카테고리 목록", example = "[\"백엔드\"]")
            List<String> matchedCategories,
            @Schema(description = "벡터 검색 시 확장된 키워드 목록 (현재 항상 빈 배열)")
            List<String> expandedKeywords
    ) {}
}

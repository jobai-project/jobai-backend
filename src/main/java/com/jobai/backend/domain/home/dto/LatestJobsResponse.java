package com.jobai.backend.domain.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record LatestJobsResponse(
        @Schema(description = "필터 조건에 맞는 전체 공고 수 (현재 페이지 크기와 무관)", example = "1234")
        long totalCount,
        @Schema(description = "더 불러올 공고가 남아있는지 여부 (offset+size < totalCount)", example = "true")
        boolean hasMore,
        @Schema(description = "최신순(createdAt DESC)으로 정렬된 공고 목록 (요청한 size만큼, 없으면 빈 배열)")
        List<LatestJob> jobs
) {
    public record LatestJob(
            @Schema(description = "공고 고유 ID", example = "101")
            Long id,
            @Schema(description = "공고 출처. 값: PUBLIC(공기업), PRIVATE(사기업)", example = "PUBLIC")
            String source,
            @Schema(description = "회사명/기관명", example = "한국전력공사")
            String companyName,
            @Schema(description = "공고 제목", example = "2026년 신입사원 채용")
            String title,
            @Schema(description = "마감까지 남은 일수 (디데이). 마감일이 없는 상시채용 등은 null", example = "5", nullable = true)
            Integer dDay,
            @Schema(description = "근무지역", example = "서울")
            String location,
            @Schema(description = "고용형태 원본 문자열 (공기업=recrutType, 사기업=employmentType)", example = "정규직")
            String employmentType
    ) {
        public static LatestJob of(
                Long id, String source, String companyName, String title,
                LocalDate deadline, String location, String employmentType
        ) {
            Integer dDay = deadline == null
                    ? null
                    : (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
            return new LatestJob(id, source, companyName, title, dDay, location, employmentType);
        }
    }
}

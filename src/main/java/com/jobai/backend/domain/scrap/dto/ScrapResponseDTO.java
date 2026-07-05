package com.jobai.backend.domain.scrap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class ScrapResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddResultDTO {
        @Schema(description = "스크랩 기록 고유 ID", example = "1")
        private Long scrapId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrapItemDTO {
        @Schema(description = "스크랩 기록 고유 ID", example = "1")
        private Long scrapId;

        @Schema(description = "공고 출처. 값: PUBLIC(공기업), PRIVATE(사기업)", example = "PUBLIC")
        private String source;

        @Schema(description = "공고 고유 ID", example = "823")
        private Long sourceId;

        @Schema(description = "회사명/기관명", example = "한국전력공사")
        private String companyName;

        @Schema(description = "공고 제목", example = "2026년도 하반기 대졸수준 채용공고")
        private String title;

        @Schema(description = "근무지역", example = "서울")
        private String location;

        @Schema(description = "고용형태 원본 문자열", example = "정규직")
        private String employmentType;

        @Schema(description = "마감까지 남은 일수 (디데이). 마감일이 없거나 이미 지난 경우 등은 null/음수일 수 있음", example = "5", nullable = true)
        private Integer dDay;

        @Schema(description = "스크랩한 시각")
        private LocalDateTime scrappedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrapListDTO {
        @Schema(description = "스크랩한 공고 목록 (최근 스크랩순, 없으면 빈 배열)")
        private List<ScrapItemDTO> scraps;
    }
}

package com.jobai.backend.domain.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record JobSearchRequest(
        @Schema(description = "검색어 (필수, 자연어 문장 또는 키워드 조합)", example = "서울 신입 백엔드")
        @NotBlank String query,
        @Schema(description = "페이지 번호 (0부터 시작)", example = "0")
        @Min(0) int page,
        @Schema(description = "페이지당 결과 수 (1~100)", example = "20")
        @Min(1) @Max(100) int size
) {
}

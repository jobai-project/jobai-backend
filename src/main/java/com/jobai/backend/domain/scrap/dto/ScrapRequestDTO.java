package com.jobai.backend.domain.scrap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ScrapRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddScrapDTO {
        @Schema(description = "공고 출처 (필수). 값: PUBLIC(공기업), PRIVATE(사기업)", example = "PUBLIC")
        @NotBlank(message = "공고 출처는 필수 입력 항목입니다.")
        private String source;

        @Schema(description = "스크랩할 공고의 고유 ID (필수)", example = "823")
        @NotNull(message = "공고 ID는 필수 입력 항목입니다.")
        private Long sourceId;
    }
}

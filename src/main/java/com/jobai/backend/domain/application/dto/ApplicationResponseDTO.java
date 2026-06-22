package com.jobai.backend.domain.application.dto;

import lombok.*;

public class ApplicationResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResultDTO {
        private Long applicationId; // 지원 현황 ID(FK)
    }
}
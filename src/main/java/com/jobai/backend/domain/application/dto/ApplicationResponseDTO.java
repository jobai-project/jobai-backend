package com.jobai.backend.domain.application.dto;

import com.jobai.backend.domain.application.entity.ApplicationStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class ApplicationResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResultDTO {
        private Long applicationId; // 지원 현황 ID(FK)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationListItemDTO {
        private Long applicationId;
        private String companyName;
        private String jobTitle;
        private ApplicationStatus status;
        private String statusLabel; // "서류 제출" 등 프론트엔드가 바로 뿌릴 수 있는 한글 텍스트
        private LocalDate appliedAt;
        private LocalDate interviewAt;
        private String memo;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationListDTO {
        private List<ApplicationListItemDTO> applications; // 리스트를 오브젝트로 한 번 감싸서 확장성 확보
    }
}
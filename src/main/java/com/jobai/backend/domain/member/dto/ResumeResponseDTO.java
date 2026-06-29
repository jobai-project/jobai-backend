package com.jobai.backend.domain.member.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class ResumeResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeItemDTO {
        private Long resumeId;
        private String originalFilename;
        private String storedFileUrl;
        private String fileSize;
        private Boolean isActive;
        private LocalDate uploadedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeListDTO {
        private List<ResumeItemDTO> resumes;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadResultDTO {
        private Long resumeId;
    }
}

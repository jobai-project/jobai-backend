package com.jobai.backend.domain.application.dto;

import com.jobai.backend.domain.application.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

public class ApplicationRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateApplicationDTO {
        @NotBlank(message = "회사명은 필수 입력 항목입니다.")
        private String companyName;

        @NotBlank(message = "공고명(직무)은 필수 입력 항목입니다.")
        private String jobTitle;

        @NotNull(message = "지원 단계는 필수 입력 항목입니다.")
        private ApplicationStatus status; // 프론트엔드에서 "DOCUMENT_SUBMITTED" 문자열을 보내면 자동 바인딩

        private LocalDate appliedAt;   // 지원일 (선택 가능)
        private LocalDate interviewAt; // 면접일 (선택 가능)
        private String memo;           // 메모 (선택 가능)
    }
}

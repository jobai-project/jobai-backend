package com.jobai.backend.domain.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jobai.backend.domain.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

public class ApplicationRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateApplicationDTO {
        @Schema(description = "회사명 (필수, 최대 50자)", example = "카카오")
        @NotBlank(message = "회사명은 필수 입력 항목입니다.")
        @Size(max = 50, message = "회사명은 50글자 이하여야 합니다.")
        private String companyName;

        @Schema(description = "지원 직무명 (필수, 최대 50자)", example = "백엔드 개발자")
        @NotBlank(message = "공고명(직무)은 필수 입력 항목입니다.")
        @Size(max = 50, message = "직무명은 50글자 이하여야 합니다.")
        private String jobTitle;

        @Schema(description = "지원 단계 (필수). 허용값: PLANNED, APPLIED, DOCUMENT_PASSED, "
                + "INTERVIEW_PASSED, FINAL_ACCEPTED, DOCUMENT_REJECTED, INTERVIEW_REJECTED",
                example = "APPLIED")
        @NotNull(message = "지원 단계는 필수 입력 항목입니다.")
        private ApplicationStatus status; // 프론트엔드에서 "DOCUMENT_SUBMITTED" 문자열을 보내면 자동 바인딩

        @Schema(description = "지원일 (선택, yyyy-MM-dd)", example = "2025-06-15", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
        private LocalDate appliedAt;   // 지원일 (선택 가능)

        @Schema(description = "면접일 (선택, yyyy-MM-dd)", example = "2025-06-25", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
        private LocalDate interviewAt; // 면접일 (선택 가능)

        @Schema(description = "메모 (선택)", example = "코딩 테스트 통과 후 지원", nullable = true)
        private String memo;           // 메모 (선택 가능)
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateApplicationDTO {

        @Schema(description = "회사명 (선택, 보내지 않으면 기존 값 유지, 최대 50자)", example = "라인", nullable = true)
        @Size(max = 50, message = "회사명은 50글자 이하여야 합니다.")
        private String companyName;

        @Schema(description = "지원 직무명 (선택, 보내지 않으면 기존 값 유지, 최대 50자)", example = "iOS 개발자", nullable = true)
        @Size(max = 50, message = "직무명은 50글자 이하여야 합니다.")
        private String jobTitle;

        @Schema(description = "지원 단계 (선택, 보내지 않으면 기존 값 유지). 허용값: PLANNED, APPLIED, DOCUMENT_PASSED, "
                + "INTERVIEW_PASSED, FINAL_ACCEPTED, DOCUMENT_REJECTED, INTERVIEW_REJECTED",
                example = "INTERVIEW_PASSED", nullable = true)
        private ApplicationStatus status;

        @Schema(description = "지원일 (선택, yyyy-MM-dd)", example = "2025-06-15", nullable = true)
        private LocalDate appliedAt;

        @Schema(description = "면접일 (선택, yyyy-MM-dd)", example = "2025-07-10", nullable = true)
        private LocalDate interviewAt;

        @Schema(description = "메모 (선택)", example = "1차 면접 통과", nullable = true)
        private String memo;
    }
}

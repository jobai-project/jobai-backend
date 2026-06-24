package com.jobai.backend.domain.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jobai.backend.domain.application.entity.ApplicationStatus;
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
        @NotBlank(message = "회사명은 필수 입력 항목입니다.")
        @Size(max = 50, message = "회사명은 50글자 이하여야 합니다.")
        private String companyName;

        @NotBlank(message = "공고명(직무)은 필수 입력 항목입니다.")
        @Size(max = 50, message = "직무명은 50글자 이하여야 합니다.")
        private String jobTitle;

        @NotNull(message = "지원 단계는 필수 입력 항목입니다.")
        private ApplicationStatus status; // 프론트엔드에서 "DOCUMENT_SUBMITTED" 문자열을 보내면 자동 바인딩

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
        private LocalDate appliedAt;   // 지원일 (선택 가능)

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "Asia/Seoul")
        private LocalDate interviewAt; // 면접일 (선택 가능)
        private String memo;           // 메모 (선택 가능)
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateApplicationDTO {

        // 사용자가 인라인으로 한 필드씩 수정할 수 있는 화면 요구사항을 고려해서 NotNull 어노테이션 제거
        @Size(max = 50, message = "회사명은 50글자 이하여야 합니다.")
        private String companyName;

        @Size(max = 50, message = "직무명은 50글자 이하여야 합니다.")
        private String jobTitle;

        private ApplicationStatus status;
        private LocalDate appliedAt;
        private LocalDate interviewAt;
        private String memo;
    }
}

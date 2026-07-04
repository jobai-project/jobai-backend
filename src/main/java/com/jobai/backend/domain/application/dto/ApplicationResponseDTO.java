package com.jobai.backend.domain.application.dto;

import com.jobai.backend.domain.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class ApplicationResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateResultDTO {
        @Schema(description = "생성된 지원 현황의 고유 ID", example = "1")
        private Long applicationId; // 지원 현황 ID(FK)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationListItemDTO {
        @Schema(description = "지원 현황 고유 ID (수정·삭제 시 사용)", example = "1")
        private Long applicationId;

        @Schema(description = "회사명", example = "카카오")
        private String companyName;

        @Schema(description = "지원 직무명", example = "백엔드 개발자")
        private String jobTitle;

        @Schema(description = "지원 단계 (영문 enum 값)", example = "APPLIED")
        private ApplicationStatus status;

        @Schema(description = "지원 단계 한글 텍스트 (UI에 바로 표시 가능)", example = "서류 지원 완료")
        private String statusLabel; // "서류 제출" 등 프론트엔드가 바로 뿌릴 수 있는 한글 텍스트

        @Schema(description = "지원일 (yyyy-MM-dd, null 가능)", example = "2025-06-15", nullable = true)
        private LocalDate appliedAt;

        @Schema(description = "면접일 (yyyy-MM-dd, null 가능)", example = "2025-06-25", nullable = true)
        private LocalDate interviewAt;

        @Schema(description = "메모 (null 가능)", example = "코딩 테스트 통과 후 지원", nullable = true)
        private String memo;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationListDTO {
        @Schema(description = "지원 현황 목록 (없으면 빈 배열)")
        private List<ApplicationListItemDTO> applications; // 리스트를 오브젝트로 한 번 감싸서 확장성 확보
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationSummaryDTO {
        @Schema(description = "지원 예정/불합격 건을 제외한 지원 현황들의 진행도 평균치 (0~100)", example = "45.0")
        private double averageProgress;   // 모든 지원 목록의 진행도 평균치

        @Schema(description = "평균 계산에 포함된 지원 현황 개수", example = "3")
        private int totalCalculatedCount; // 계산에 포함된 공고 개수
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingScheduleItemDTO {
        @Schema(description = "지원 현황 고유 ID", example = "1")
        private Long applicationId;

        @Schema(description = "회사명", example = "카카오")
        private String companyName;

        @Schema(description = "지원 직무명", example = "백엔드 개발자")
        private String jobTitle;

        @Schema(description = "면접일 (yyyy-MM-dd)", example = "2026-07-10")
        private LocalDate interviewAt;

        @Schema(description = "면접일까지 남은 일수 (디데이). 0이면 D-Day, 3이면 D-3", example = "3")
        private long daysLeft; // 디데이 계산용 필드 (ex. 0이면 D-Day, 3이면 D-3)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingScheduleListDTO {
        @Schema(description = "다가오는 면접 일정 목록 (최대 3건, 없으면 빈 배열)")
        private List<UpcomingScheduleItemDTO> schedules;
    }
}

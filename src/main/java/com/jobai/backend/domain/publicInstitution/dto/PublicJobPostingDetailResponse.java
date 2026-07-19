package com.jobai.backend.domain.publicInstitution.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record PublicJobPostingDetailResponse(
        @Schema(description = "공고 고유 ID", example = "1")
        Long id,
        @Schema(description = "공고 제목", example = "2026년도 하반기 대졸수준 채용공고")
        String title,
        @Schema(description = "기관명", example = "한국전력공사")
        String companyName,
        @Schema(description = "기업형태 (현재 수집 단계에서 대부분 비어있을 수 있음)", example = "공기업", nullable = true)
        String companyType,
        @Schema(description = "고용형태 (정규직/계약직 등)", example = "정규직")
        String recrutType,             // 고용형태 (정규직/계약직 등)
        @Schema(description = "경력구분. 값 예: 신입, 경력, 신입+경력", example = "신입")
        String workExperience,         // 경력구분 (신입/경력)
        @Schema(description = "근무지역 (콤마로 구분된 다중 지역일 수 있음)", example = "서울,광주,전남")
        String workRegion,             // 근무지역
        @Schema(description = "접수 시작일", example = "2026-05-15")
        LocalDate beginDate,           // 접수 시작일
        @Schema(description = "접수 마감일. null이면 상시모집 등 마감일이 없는 공고", example = "2026-07-16", nullable = true)
        LocalDate endDate,             // 접수 마감일 (null이면 상시모집 등)
        @Schema(description = "모집직무 (NCS 코드명 리스트)", example = "IT/인터넷")
        String jobRole,                // 모집직무 (NCS 코드명 리스트)
        @Schema(description = "지원자격", example = "학력 및 전공 무관, 병역필 또는 면제자")
        String applyQualification,     // 지원자격
        @Schema(description = "결격사유", example = "공사 인사규정 제10조 결격사유에 해당하는 자")
        String disqualificationReason, // 결격사유
        @Schema(description = "접수방법", example = "온라인 접수(공사 채용 홈페이지)")
        String applicationMethod,      // 접수방법
        @Schema(description = "지원(원서 접수) 링크", example = "https://recruit.kepco.co.kr")
        String applyLink,              // 지원 링크
        @Schema(description = "마감 여부", example = "false")
        boolean isClosed,              // 마감 여부
        @Schema(description = "활성 이력서 기준으로 저장된 실제 AI 매칭점수. 점수가 아직 산출되지 않았거나 활성 이력서가 없으면 null", example = "88", nullable = true)
        Integer matchScore,
        @Schema(description = "저장된 실제 AI 매칭점수의 산정 사유. 점수가 아직 산출되지 않았거나 활성 이력서가 없으면 null", example = "직무 역량과 보유 기술이 높은 수준으로 일치합니다.", nullable = true)
        String scoreReason,
        @Schema(description = "공고 상세 본문. 원본 직무기술서(PDF)를 파싱한 텍스트/HTML로, \"공고 상세\" 영역에 그대로 렌더링하면 됨")
        String htmlContent             // 공고 상세 (직무기술서 파싱 내용)
) {
}

package com.jobai.backend.domain.publicInstitution.dto;

import java.time.LocalDate;

public record PublicJobPostingDetailResponse(
        Long id,
        String title,
        String companyName,
        String companyType,
        String recrutType,             // 고용형태 (정규직/계약직 등)
        String workExperience,         // 경력구분 (신입/경력)
        String workRegion,             // 근무지역
        LocalDate beginDate,           // 접수 시작일
        LocalDate endDate,             // 접수 마감일 (null이면 상시모집 등)
        String jobRole,                // 모집직무 (NCS 코드명 리스트)
        String applyQualification,     // 지원자격
        String disqualificationReason, // 결격사유
        String applicationMethod,      // 접수방법
        String applyLink,              // 지원 링크
        boolean isClosed,              // 마감 여부
        String htmlContent             // 공고 상세 (직무기술서 파싱 내용)
) {
}

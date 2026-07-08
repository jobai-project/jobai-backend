package com.jobai.backend.domain.home.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 공기업/사기업 후보 공고를 소스 무관하게 다루기 위한 내부 전송 객체.
 * HomeJobCandidateRepository -> HomeRecommendationService 간에만 쓰인다.
 */
public record JobCandidate(
        Long id,
        String source,          // "PUBLIC" | "PRIVATE"
        String companyName,
        String title,
        String location,
        String employmentType,
        String jobCategory,
        LocalDate deadline,
        LocalDateTime createdAt
) {
}

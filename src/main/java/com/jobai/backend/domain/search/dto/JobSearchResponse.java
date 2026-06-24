package com.jobai.backend.domain.search.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record JobSearchResponse(
        long totalCount,
        List<JobSummary> jobs,
        SearchInfo searchInfo
) {

    public record JobSummary(
            Long id,
            String source,
            String title,
            String company,
            String location,
            String jobCategory,
            String employmentType,
            String applyUrl,
            LocalDate deadline,
            LocalDateTime createdAt
    ) {}

    public record SearchInfo(
            String method,
            List<String> matchedCategories,
            List<String> expandedKeywords
    ) {}
}

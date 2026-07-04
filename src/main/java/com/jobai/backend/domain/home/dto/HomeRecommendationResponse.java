package com.jobai.backend.domain.home.dto;

import java.time.LocalDate;
import java.util.List;

public record HomeRecommendationResponse(
        long totalCount,
        boolean hasMore,
        List<RecommendedJob> jobs
) {
    public record RecommendedJob(
            Long id,
            String source,          // "PUBLIC" | "PRIVATE"
            String companyName,
            String title,
            int matchScore,         // 0~100, 현재는 Mock (TODO: AI팀 실제 로직으로 교체)
            Integer dDay,           // 마감일 - 오늘, 상시채용 등 마감일 없으면 null
            String location,
            String employmentType   // 원본 표시용 문자열 그대로 (공기업=recrutType, 사기업=employmentType)
    ) {
        public static RecommendedJob of(
                Long id, String source, String companyName, String title,
                int matchScore, LocalDate deadline, String location, String employmentType
        ) {
            Integer dDay = deadline == null
                    ? null
                    : (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
            return new RecommendedJob(id, source, companyName, title, matchScore, dDay, location, employmentType);
        }
    }
}

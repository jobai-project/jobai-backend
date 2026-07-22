package com.jobai.backend.domain.search.dto;

import java.util.List;

/**
 * 검색 필터 조건.
 *
 * <p>exactGroups / semanticGroups는 RequirementGroup 단위로 표현된다.
 * 그룹 간은 AND, 그룹 내 terms는 OR로 SQL에 적용된다.
 *
 * <p>unknownExperience=true 이면 경력 조건을
 * {@code IN (...) OR IS NULL} 로 확장하여 미확인/미입력 공고를 포함한다.
 */
public record SearchCondition(
        List<String> categories,
        String company,
        String location,
        String experience,
        List<String> experienceLevels,
        List<String> employmentTypes,
        String method,
        String sourceType,
        List<RequirementGroup> exactGroups,
        List<RequirementGroup> semanticGroups
) {
    public static final String METHOD_KEYWORD = "KEYWORD";
    public static final String METHOD_VECTOR  = "VECTOR";
    public static final String METHOD_HYBRID  = "HYBRID";
}

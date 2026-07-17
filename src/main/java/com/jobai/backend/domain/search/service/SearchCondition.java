package com.jobai.backend.domain.search.service;

import java.util.List;

// 나중에 점수 추가
public record SearchCondition(
        List<String> categories,
        String company,
        String location,
        String experience,
        List<String> experienceLevels,
        List<String> employmentTypes,
        String method
) {
    public static final String METHOD_KEYWORD = "KEYWORD";
    public static final String METHOD_VECTOR = "VECTOR";
    public static final String METHOD_HYBRID = "HYBRID";
}

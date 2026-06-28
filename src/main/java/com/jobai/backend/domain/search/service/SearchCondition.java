package com.jobai.backend.domain.search.service;

import java.util.List;

public record SearchCondition(
        List<String> categories,
        List<String> fallbackCategories,
        List<String> titleKeywords,
        String location,
        String experience,
        String method
) {
    public static final String METHOD_KEYWORD = "KEYWORD";
    public static final String METHOD_VECTOR = "VECTOR";
}

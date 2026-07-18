package com.jobai.backend.global.model;

import java.util.Arrays;
import java.util.Map;

/**
 * 고용형태 분류. 크롤러 raw값을 정규화하거나 LLM이 공고 제목에서 추출할 때 사용한다.
 */
public enum EmploymentType {

    FULL_TIME("정규직"),
    CONTRACT("계약직"),
    INTERN("인턴"),
    MILITARY_EXCEPTION("병역특례"),
    UNKNOWN("미확인");

    private final String label;

    EmploymentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // 크롤러 raw값 → 정규화 enum 매핑
    private static final Map<String, EmploymentType> RAW_MAPPING = Map.of(
            "FULL_TIME_WORKER", FULL_TIME,
            "INTERN_WORKER", INTERN,
            "MILITARY_SERVICE_EXCEPTION", MILITARY_EXCEPTION,
            "정규", FULL_TIME,
            "인턴", INTERN,
            "계약", CONTRACT
    );

    /** 크롤러 raw값을 정규화. null이면 null 반환(LLM 분류 대상). */
    public static EmploymentType fromRawValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return RAW_MAPPING.getOrDefault(raw.trim(), null);
    }

    /** LLM 출력 라벨 → enum. 목록 밖이면 UNKNOWN. */
    public static EmploymentType fromLabel(String label) {
        if (label == null) return UNKNOWN;
        String trimmed = label.trim();
        return Arrays.stream(values())
                .filter(e -> e.label.equals(trimmed))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /** 프롬프트에 넣을 라벨 목록 (콤마 구분). */
    public static String labelsForPrompt() {
        StringBuilder sb = new StringBuilder();
        for (EmploymentType e : values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(e.label);
        }
        return sb.toString();
    }
}

package com.jobai.backend.domain.crawler.classify;

import java.util.Arrays;

/**
 * 경력 구분. LLM이 공고 제목에서 신입/경력 여부를 추출할 때 사용한다.
 */
public enum ExperienceLevel {

    NEWCOMER("신입"),
    EXPERIENCED("경력"),
    ANY("무관"),
    UNKNOWN("미확인");

    private final String label;

    ExperienceLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** LLM 출력 라벨 → enum. 목록 밖이면 UNKNOWN. */
    public static ExperienceLevel fromLabel(String label) {
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
        for (ExperienceLevel e : values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(e.label);
        }
        return sb.toString();
    }
}

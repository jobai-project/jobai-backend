package com.jobai.backend.domain.crawler.classify;

import java.util.Arrays;

/**
 * 직무 분류 택소노미. LLM 이 공고 제목을 보고 이 중 하나로 분류한다.
 * 매칭 대상(개발 11 + 디자이너 + PM기획)과 제외(비대상/미분류)로 나뉜다.
 */
public enum JobCategory {

    // --- 개발 (매칭 대상) ---
    BACKEND("백엔드", true),
    FRONTEND("프론트엔드", true),
    FULLSTACK("풀스택", true),
    MOBILE("모바일", true),
    AI_ML("AI/ML", true),
    DATA_ENGINEERING("데이터엔지니어링", true),
    DEVOPS("DevOps/인프라", true),
    SECURITY("보안", true),
    QA("QA/테스트", true),
    EMBEDDED("임베디드", true),
    ETC_DEV("기타개발", true),

    // --- 비개발 (매칭 대상) ---
    DESIGNER("디자이너", true),
    PM("PM/기획", true),

    // --- 제외 ---
    NON_TARGET("비대상", false),    // 영업·마케팅·HR·재무 등
    UNCLASSIFIED("미분류", false);  // 제목만으론 판단 불가

    private final String label;
    private final boolean matchTarget;

    JobCategory(String label, boolean matchTarget) {
        this.label = label;
        this.matchTarget = matchTarget;
    }

    public String getLabel() {
        return label;
    }

    public boolean isMatchTarget() {
        return matchTarget;
    }

    /** 라벨 문자열 → enum. 목록 밖 답이면 UNCLASSIFIED 로 안전하게. */
    public static JobCategory fromLabel(String label) {
        if (label == null) return UNCLASSIFIED;
        String trimmed = label.trim();
        return Arrays.stream(values())
                .filter(c -> c.label.equals(trimmed))
                .findFirst()
                .orElse(UNCLASSIFIED);
    }

    /** 프롬프트에 넣을 라벨 목록 (콤마 구분). */
    public static String labelsForPrompt() {
        StringBuilder sb = new StringBuilder();
        for (JobCategory c : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.label);
        }
        return sb.toString();
    }
}

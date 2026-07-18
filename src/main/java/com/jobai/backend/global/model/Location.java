package com.jobai.backend.global.model;

import java.util.Arrays;

/**
 * 지역 대분류. LLM이 공고의 location 필드를 정규화할 때 사용한다.
 */
public enum Location {

    SEOUL("서울"),
    GYEONGGI("경기"),
    INCHEON("인천"),
    BUSAN("부산"),
    DAEGU("대구"),
    GWANGJU("광주"),
    DAEJEON("대전"),
    ULSAN("울산"),
    SEJONG("세종"),
    GANGWON("강원"),
    CHUNGBUK("충북"),
    CHUNGNAM("충남"),
    JEONBUK("전북"),
    JEONNAM("전남"),
    GYEONGBUK("경북"),
    GYEONGNAM("경남"),
    JEJU("제주"),
    REMOTE("원격"),
    OVERSEAS("해외"),
    UNKNOWN("미확인");

    private final String label;

    Location(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** LLM 출력 라벨 → enum. 목록 밖이면 UNKNOWN. */
    public static Location fromLabel(String label) {
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
        for (Location r : values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(r.label);
        }
        return sb.toString();
    }
}

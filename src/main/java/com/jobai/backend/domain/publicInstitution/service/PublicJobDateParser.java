package com.jobai.backend.domain.publicInstitution.service;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


/*
    공통으로 사용되는 날짜 파싱 로직을 유틸리티 클래스로 추출하여
    JobDataSyncService와 JobDetailSyncService에서 재사용
 */
@Slf4j
public class PublicJobDateParser {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.replaceAll("-", ""), DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("날짜 포맷 파싱 실패: {}", dateStr);
            return null;
        }
    }
}

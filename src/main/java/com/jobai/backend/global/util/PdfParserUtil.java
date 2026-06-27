package com.jobai.backend.global.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PdfParserUtil {

    // "소분류 : 정보기술개발" 혹은 "소분류 정보기술개발" 포맷을 찾아내기 위한 전역 정규식 패턴
    private static final Pattern NCS_SUB_PATTERN = Pattern.compile("소분류\\s*[:\\s]\\s*([^\\n\\r|]+)");

    /**
     * PDF 파일의 바이너리 바이트 배열을 받아 순수 텍스트를 추출합니다.
     */
    public String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) return "";

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // 줄바꿈과 표 내부 텍스트 순서 최대한 유지
            return sanitize(stripper.getText(document));
        } catch (Exception e) {
            log.error("PDF 텍스트 덤프 중 에러 발생: {}", e.getMessage());
            return "";
        }
    }

    // PostgreSQL TEXT 컬럼은 null byte(\0)를 허용하지 않으므로 제거
    public static String sanitize(String text) {
        if (text == null) return "";
        return text.replace("\0", "");
    }

    /**
     * 추출된 직무기술서 전체 텍스트에서 NCS 소분류 키워드를 정규식으로 매칭합니다.
     */
    public String parseNcsSubCategory(String fullText) {
        if (fullText == null || fullText.isBlank()) return "미분류";

        Matcher matcher = NCS_SUB_PATTERN.matcher(fullText);
        if (matcher.find()) {
            String subCategory = matcher.group(1).trim();
            // 대괄호 및 양끝 공백 제거 후 반환
            return subCategory.replaceAll("[\\[\\]\\s]", "");
        }

        // 공공기관 서식이 깨져 정규식이 실패할 경우를 대비한 가벼운 2차 키워드 매칭 우회책
        if (fullText.contains("정보기술개발")) return "정보기술개발";
        if (fullText.contains("데이터베이스")) return "데이터베이스구축";
        if (fullText.contains("정보기술운영")) return "정보기술운영";

        return "일반무구분";
    }
}

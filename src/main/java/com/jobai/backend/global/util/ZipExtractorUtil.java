package com.jobai.backend.global.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZipExtractorUtil {

    private final PdfParserUtil pdfParserUtil;
    private final HwpParserUtil hwpParserUtil;

    private static final List<String> IT_KEYWORDS = List.of(
            "정보", "it", "ict", "전산", "보안", "데이터", "개발", "소프트웨어", "sw", "디지털", "ai", "인공지능", "네트워크", "시스템"
    );

    /**
     * ZIP 바이트에서 PDF/HWP 파일을 찾아 텍스트를 추출합니다.
     * 파일이 여러 개이면 IT/정보통신 관련 파일명만 파싱합니다.
     * 파일이 하나뿐이면 파일명과 무관하게 파싱합니다.
     */
    public String extractText(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) return "";

        // 1차 패스: 파싱 대상 후보 수집
        record ZipFile(String name, byte[] bytes) {}
        List<ZipFile> candidates = new ArrayList<>();

        // 공공기관 ZIP은 EUC-KR(CP949)로 인코딩된 파일명을 가질 수 있음
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), Charset.forName("EUC-KR"))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String name = entry.getName();
                    String lower = name.toLowerCase();
                    if (lower.endsWith(".pdf") || lower.endsWith(".hwp")) {
                        candidates.add(new ZipFile(name, zis.readAllBytes()));
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("ZIP 압축 해제 중 에러 발생: {}", e.getMessage());
            return "";
        }

        if (candidates.isEmpty()) return "";

        // 단일 파일이면 파일명과 무관하게 파싱
        List<ZipFile> targets = candidates.size() == 1
                ? candidates
                : candidates.stream().filter(f -> isItRelated(f.name())).toList();

        if (targets.isEmpty()) {
            log.info("ZIP 내 IT/정보통신 관련 파일 없음 (총 {}개 파일 스킵)", candidates.size());
            return "";
        }

        // 2차 패스: 대상 파일 파싱
        StringBuilder sb = new StringBuilder();
        for (ZipFile f : targets) {
            String lower = f.name().toLowerCase();
            String text = lower.endsWith(".hwp")
                    ? hwpParserUtil.extractText(f.bytes())
                    : pdfParserUtil.extractText(f.bytes());

            if (!text.isBlank()) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(text);
                log.info("ZIP 내 파일 파싱 완료: {} ({} chars)", f.name(), text.length());
            }
        }

        return sb.toString();
    }

    private boolean isItRelated(String filename) {
        String lower = filename.toLowerCase();
        return IT_KEYWORDS.stream().anyMatch(lower::contains);
    }
}

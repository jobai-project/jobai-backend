package com.jobai.backend.global.util;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Slf4j
@Component
public class HwpParserUtil {

    /**
     * HWP 파일의 바이너리 바이트 배열을 받아 순수 텍스트를 추출합니다.
     * hwplib 공식 TextExtractor를 사용하여 표/셀/컨트롤 텍스트까지 포함합니다.
     */
    public String extractText(byte[] hwpBytes) {
        if (hwpBytes == null || hwpBytes.length == 0) return "";

        try (ByteArrayInputStream is = new ByteArrayInputStream(hwpBytes)) {
            HWPFile hwpFile = HWPReader.fromInputStream(is);
            // InsertControlTextBetweenParagraphText: 표 셀 등 컨트롤 텍스트도 문단 사이에 포함
            String raw = TextExtractor.extract(hwpFile, TextExtractMethod.InsertControlTextBetweenParagraphText);
            return normalize(PdfParserUtil.sanitize(raw));
        } catch (Exception e) {
            log.error("HWP 텍스트 추출 중 에러 발생: {}", e.getMessage());
            return "";
        }
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text
                // 3개 이상 연속 개행 → 2개로 압축
                .replaceAll("(\r?\n){3,}", "\n\n")
                // 줄 끝 공백 제거
                .replaceAll("[ \\t]+(\r?\n)", "$1")
                .trim();
    }
}

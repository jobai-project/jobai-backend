package com.jobai.backend.domain.privatejobposting.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * 공고 본문(description)의 HTML 노이즈를 제거한다.
 *
 * <p>수집된 description 은 회사에 따라 형태가 다르다:
 * <ul>
 *   <li>평범한 HTML — {@code <p>회사 소개</p>}</li>
 *   <li>엔티티 인코딩 HTML — {@code &lt;p&gt;회사 소개&lt;/p&gt;} (수집 중 이스케이프된 경우)</li>
 *   <li>평문 — 태그 없는 일반 텍스트</li>
 * </ul>
 * 어느 경우든 태그·엔티티를 제거하고 순수 텍스트로 만들되, 문단/줄바꿈 구조는 보존한다.
 *
 * <p>정제는 export 시점에만 수행하며 DB 의 원본 description 은 건드리지 않는다.
 * 포맷을 바꾸고 싶으면 이 클래스만 수정해 재export 하면 된다.
 */
@Component
public class JobDescriptionCleanser {

    /** 줄바꿈으로 취급할 블록 요소들. */
    private static final String BLOCK_TAGS =
            "p, div, li, h1, h2, h3, h4, h5, h6, tr, section, article";

    /**
     * HTML 본문을 정제해 순수 텍스트로 변환한다. 문단 줄바꿈은 보존.
     *
     * @param raw 원본 description (HTML·엔티티인코딩·평문·null 모두 허용)
     * @return 태그·엔티티 제거된 텍스트. 입력이 null/빈값이면 빈 문자열.
     */
    public String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        // 이중 인코딩 대응: 입력에 HTML 엔티티(&lt; 등)가 남아 있으면 먼저 디코드해
        // "&lt;p&gt;" 를 평범한 "<p>" 로 되돌린 뒤 파싱한다.
        // (Jsoup.parse 는 텍스트 노드 안의 엔티티를 태그로 되살리진 않으므로 선행 디코드가 필요.)
        if (raw.contains("&lt;") || raw.contains("&gt;") || raw.contains("&amp;")) {
            raw = Parser.unescapeEntities(raw, false);
        }

        Document doc = Jsoup.parse(raw);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));

        // <br> 은 개행 텍스트로 치환.
        doc.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
        // 블록 요소 앞에 개행 텍스트 삽입 → 문단 분리 보존.
        for (Element el : doc.select(BLOCK_TAGS)) {
            el.before(new TextNode("\n"));
        }

        String text = doc.wholeText();

        // 줄 앞뒤 공백 정리 + 3줄 이상 연속 개행을 2줄로 축소.
        text = text.replaceAll("[ \\t\\x0B\\f\\r]*\\n[ \\t\\x0B\\f\\r]*", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return text;
    }
}
package com.jobai.backend.domain.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.llm.AnthropicClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryInterpreter {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            너는 채용 공고 검색 시스템의 쿼리 분석기이다.
            사용자의 자연어 입력을 분석하여 구조화된 검색 조건을 JSON으로 반환하라.

            사용 가능한 직무 카테고리:
            BACKEND(백엔드), FRONTEND(프론트엔드), FULLSTACK(풀스택), MOBILE(모바일),
            AI_ML(AI/ML), DATA_ENGINEERING(데이터엔지니어링), DEVOPS(DevOps/인프라),
            SECURITY(보안), QA(QA/테스트), EMBEDDED(임베디드), ETC_DEV(기타개발),
            DESIGNER(디자이너), PM(PM/기획)

            반드시 위 카테고리의 영문 이름 중에서만 선택하라.

            응답 형식 (JSON만 반환, 다른 텍스트 없이):
            {
              "categories": ["PRIMARY_CATEGORY"],
              "fallbackCategories": ["SECONDARY_CATEGORY"],
              "titleKeywords": ["키워드1", "키워드2"],
              "location": null,
              "experience": null
            }
            """;

    public SearchCondition interpret(String query) {
        String response = anthropicClient.complete(SYSTEM_PROMPT, query, 300);
        return parseResponse(response);
    }

    private SearchCondition parseResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            List<String> categories = toStringList(root.path("categories"));
            List<String> fallbackCategories = toStringList(root.path("fallbackCategories"));
            List<String> titleKeywords = toStringList(root.path("titleKeywords"));
            String location = nullableText(root.path("location"));
            String experience = nullableText(root.path("experience"));

            // enum 이름 → 한글 라벨 변환
            List<String> categoryLabels = convertToLabels(categories);
            List<String> fallbackLabels = convertToLabels(fallbackCategories);

            return new SearchCondition(
                    categoryLabels,
                    fallbackLabels,
                    titleKeywords,
                    location,
                    experience,
                    SearchCondition.METHOD_AI_EXPANDED
            );
        } catch (Exception e) {
            throw new RuntimeException("LLM 응답 파싱 실패: " + response, e);
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText("");
                if (!text.isBlank()) {
                    list.add(text);
                }
            }
        }
        return list;
    }

    private String nullableText(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return null;
        String text = node.asText("");
        return text.isBlank() || "null".equals(text) ? null : text;
    }

    private List<String> convertToLabels(List<String> enumNames) {
        // LLM이 반환하는 enum 이름(BACKEND 등)을 DB에 저장된 한글 라벨(백엔드 등)로 변환
        return enumNames.stream()
                .map(name -> {
                    try {
                        return com.jobai.backend.domain.crawler.classify.JobCategory
                                .valueOf(name).getLabel();
                    } catch (IllegalArgumentException e) {
                        log.warn("알 수 없는 카테고리: {}", name);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}

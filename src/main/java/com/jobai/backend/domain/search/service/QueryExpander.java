package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.search.dto.QueryExpansionResult;
import com.jobai.backend.domain.search.dto.RequirementGroup;
import com.jobai.backend.global.cache.CacheKeyGenerator;
import com.jobai.backend.global.cache.CacheNames;
import com.jobai.backend.global.llm.AnthropicClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * LLM 기반 쿼리 확장기.
 *
 * <p>unmatched 토큰을 3가지 유형의 RequirementGroup으로 분류한다.
 *
 * <ul>
 *   <li>EXACT_REQUIRED   — 공고에 반드시 등장해야 하는 기술 스택·도구명</li>
 *   <li>SEMANTIC_REQUIRED  — 필수 조건이지만 다양한 표현으로 등장하는 유의어 그룹</li>
 *   <li>SEMANTIC_PREFERRED — 선호 분위기·문화 표현의 유의어 그룹</li>
 * </ul>
 *
 * <p>같은 개념의 유의어는 OR, 서로 다른 개념 그룹은 AND로 SQL에 적용된다.
 * 비활성화되거나 LLM 호출이 실패하면 원본 쿼리를 그대로 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryExpander {

    private final AnthropicClient anthropicClient;

    @Value("${search.query-expand.enabled:false}")
    private boolean enabled;

    private static final int MAX_TOKENS = 400;

    /**
     * 출력 형식: TYPE[CONCEPT]: term1, term2
     * 예: EXACT_REQUIRED[KAFKA]: kafka, apache kafka
     */
    private static final Pattern GROUP_LINE_PATTERN = Pattern.compile(
            "^(EXACT_REQUIRED|SEMANTIC_REQUIRED|SEMANTIC_PREFERRED)\\[([A-Z0-9_]+)\\]:\\s*(.*)$"
    );

    private static final String SYSTEM_PROMPT = """
            당신은 채용공고 검색 쿼리를 분석하는 전문가입니다.
            사용자가 제공한 토큰을 다음 3가지 유형과 개념 그룹으로 분류하세요.

            [유형 정의]
            EXACT_REQUIRED: 공고 제목·본문에 반드시 있어야 하는 기술 스택·도구명
              판단 기준: "사용하는", "기반", "필수", "개발자" 와 함께 명시된 표현
              예: Kafka 기반, Redis 사용, Spring Boot 개발자

            SEMANTIC_REQUIRED: 반드시 충족해야 하지만 다양한 표현으로 등장하는 조건의 유의어 그룹
              판단 기준: 근무 형태, 필수 환경 등 의미상 필수인 조건
              예: 재택근무 → 원격근무, 리모트, WFH, remote

            SEMANTIC_PREFERRED: 선호 분위기·문화 등 의미적 표현의 유의어 그룹
              판단 기준: 조직문화, 성장환경 등 분위기 표현
              예: 자유로운 분위기 → 수평적, 자율적, 스타트업 문화

            [중요 규칙]
            - 이미 structuralFilters(카테고리, 경력, 지역 등)로 추출된 개념은 다시 분류하지 말 것
            - 각 개념을 별도 그룹으로 분리할 것 (KAFKA 그룹, REMOTE_WORK 그룹을 합치지 말 것)
            - 개념명은 영문 대문자와 밑줄만 사용 (예: REMOTE_WORK, KAFKA)

            [출력 형식] 해당하는 줄만 출력, 없으면 생략
            EXACT_REQUIRED[개념명]: 유의어1, 유의어2
            SEMANTIC_REQUIRED[개념명]: 유의어1, 유의어2, 유의어3
            SEMANTIC_PREFERRED[개념명]: 유의어1, 유의어2

            [출력 예시]
            원본 검색어: 재택근무 가능한 Kafka 쓰는 백엔드 신입
            분류 토큰: 재택근무, kafka
            이미 추출된 구조 조건: category=백엔드, experience=신입

            출력:
            EXACT_REQUIRED[KAFKA]: kafka, apache kafka
            SEMANTIC_REQUIRED[REMOTE_WORK]: 재택근무, 원격근무, 리모트, wfh, remote
            """;

    /**
     * 쿼리를 확장한다.
     *
     * @param originalQuery   사용자 원본 쿼리
     * @param unmatchedTokens 키워드 매칭에 걸리지 않은 토큰 목록
     * @return 확장 결과 (RequirementGroup 3분류 + 임베딩용 텍스트)
     */
    @Cacheable(cacheNames = CacheNames.QUERY_EXPANSION,
            key = "T(com.jobai.backend.global.cache.CacheKeyGenerator).buildKey(#originalQuery, #unmatchedTokens)",
            condition = "#unmatchedTokens != null && !#unmatchedTokens.isEmpty()")
    public QueryExpansionResult expand(String originalQuery, List<String> unmatchedTokens) {
        if (!enabled || unmatchedTokens == null || unmatchedTokens.isEmpty()) {
            return QueryExpansionResult.unchanged(originalQuery);
        }

        try {
            String userPrompt = buildUserPrompt(originalQuery, unmatchedTokens);
            String response = anthropicClient.complete(SYSTEM_PROMPT, userPrompt, MAX_TOKENS);
            QueryExpansionResult result = parseResponse(originalQuery, response);

            log.info("[쿼리확장] query={}, unmatched={}, exactRequired={}, semanticRequired={}, semanticPreferred={}",
                    originalQuery, unmatchedTokens,
                    result.exactRequired(), result.semanticRequired(), result.semanticPreferred());
            return result;
        } catch (Exception e) {
            log.warn("[쿼리확장] 실패, 원본 쿼리 사용: query={}, error={}", originalQuery, e.getMessage());
            return QueryExpansionResult.unchanged(originalQuery);
        }
    }

    private String buildUserPrompt(String originalQuery, List<String> unmatchedTokens) {
        return """
                원본 검색어: %s
                분류가 필요한 토큰: %s

                위 토큰을 유형과 개념 그룹으로 분류해주세요.
                """.formatted(originalQuery, String.join(", ", unmatchedTokens));
    }

    private static final int MAX_TERMS_PER_GROUP = 8;
    private static final int MAX_TERM_LENGTH = 30;
    private static final int MAX_GROUPS_PER_TYPE = 5;

    /**
     * LLM 응답을 파싱하여 RequirementGroup 목록으로 변환한다.
     *
     * <pre>
     * EXACT_REQUIRED[KAFKA]: kafka, apache kafka
     * SEMANTIC_REQUIRED[REMOTE_WORK]: 재택근무, 원격근무, 리모트, wfh, remote
     * SEMANTIC_REQUIRED[WORK_LIFE_BALANCE]: 워라밸, 야근 없음, 정시퇴근
     * </pre>
     */
    private QueryExpansionResult parseResponse(String originalQuery, String response) {
        if (response == null || response.isBlank()) {
            return QueryExpansionResult.unchanged(originalQuery);
        }

        List<RequirementGroup> exactRequired    = new ArrayList<>();
        List<RequirementGroup> semanticRequired  = new ArrayList<>();
        List<RequirementGroup> semanticPreferred = new ArrayList<>();

        for (String line : response.split("\n")) {
            Matcher m = GROUP_LINE_PATTERN.matcher(line.trim());
            if (!m.matches()) continue;

            String type    = m.group(1);
            String concept = m.group(2);
            List<String> terms = parseTerms(m.group(3));
            if (terms.isEmpty()) continue;

            RequirementGroup group = new RequirementGroup(concept, terms);
            switch (type) {
                case "EXACT_REQUIRED"     -> { if (exactRequired.size()    < MAX_GROUPS_PER_TYPE) exactRequired.add(group); }
                case "SEMANTIC_REQUIRED"  -> { if (semanticRequired.size()  < MAX_GROUPS_PER_TYPE) semanticRequired.add(group); }
                case "SEMANTIC_PREFERRED" -> { if (semanticPreferred.size() < MAX_GROUPS_PER_TYPE) semanticPreferred.add(group); }
            }
        }

        if (exactRequired.isEmpty() && semanticRequired.isEmpty() && semanticPreferred.isEmpty()) {
            log.warn("[쿼리확장] LLM 응답 형식 불량, 확장 건너뜀: response={}", response);
            return QueryExpansionResult.unchanged(originalQuery);
        }

        // 임베딩 텍스트: 원본 쿼리 + semantic terms
        List<String> allSemanticTerms = Stream.concat(
                semanticRequired.stream().flatMap(g -> g.terms().stream()),
                semanticPreferred.stream().flatMap(g -> g.terms().stream())
        ).distinct().toList();

        String expandedText = allSemanticTerms.isEmpty()
                ? originalQuery
                : originalQuery + " " + String.join(" ", allSemanticTerms);

        return new QueryExpansionResult(
                expandedText, allSemanticTerms,
                exactRequired, semanticRequired, semanticPreferred);
    }

    private List<String> parseTerms(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> s.length() >= 2 && s.length() <= MAX_TERM_LENGTH)
                .distinct()
                .limit(MAX_TERMS_PER_GROUP)
                .toList();
    }
}

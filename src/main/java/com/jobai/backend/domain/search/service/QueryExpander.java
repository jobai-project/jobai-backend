package com.jobai.backend.domain.search.service;

import com.jobai.backend.global.llm.AnthropicClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * LLM 기반 쿼리 확장기.
 * unmatched 토큰(키워드 매칭에 걸리지 않은 자연어 표현)을
 * 의미적으로 관련된 채용 검색 키워드로 확장한다.
 *
 * <p>비활성화되거나 LLM 호출이 실패하면 원본 쿼리를 그대로 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryExpander {

    private final AnthropicClient anthropicClient;

    @Value("${search.query-expand.enabled:false}")
    private boolean enabled;

    private static final int MAX_TOKENS = 200;

    private static final String SYSTEM_PROMPT = """
            당신은 채용공고 검색 쿼리를 확장하는 전문가입니다.
            사용자의 검색 의도를 파악하여, 채용공고 본문에서 실제로 사용될 만한
            관련 키워드를 생성해주세요.

            [규칙]
            - 입력: 원본 검색어와 의미 파악이 필요한 토큰 목록
            - 출력: 쉼표로 구분된 관련 키워드 (5~10개)
            - 채용공고 description에 실제로 등장할 법한 단어만 생성
            - 동의어, 유사 표현, 관련 기술/환경을 포함
            - 설명 없이 키워드만 출력
            - 예: "원격근무, 리모트워크, 재택, WFH, 유연근무"
            """;

    /**
     * 쿼리를 확장한다. 비활성화 상태이거나 unmatched 토큰이 없으면 원본을 그대로 반환한다.
     *
     * @param originalQuery   사용자 원본 쿼리
     * @param unmatchedTokens 키워드 매칭에 걸리지 않은 토큰 목록
     * @return 확장 결과 (확장 텍스트 + 추가된 키워드 목록)
     */
    public QueryExpansionResult expand(String originalQuery, List<String> unmatchedTokens) {
        if (!enabled || unmatchedTokens == null || unmatchedTokens.isEmpty()) {
            return QueryExpansionResult.unchanged(originalQuery);
        }

        try {
            String userPrompt = buildUserPrompt(originalQuery, unmatchedTokens);
            String response = anthropicClient.complete(SYSTEM_PROMPT, userPrompt, MAX_TOKENS);
            List<String> expandedKeywords = parseResponse(response);

            if (expandedKeywords.isEmpty()) {
                return QueryExpansionResult.unchanged(originalQuery);
            }

            String expandedText = originalQuery + " " + String.join(" ", expandedKeywords);
            log.info("[쿼리확장] query={}, unmatched={}, expanded={}", originalQuery, unmatchedTokens, expandedKeywords);
            return new QueryExpansionResult(expandedText, expandedKeywords);
        } catch (Exception e) {
            log.warn("[쿼리확장] 실패, 원본 쿼리 사용: query={}, error={}", originalQuery, e.getMessage());
            return QueryExpansionResult.unchanged(originalQuery);
        }
    }

    private String buildUserPrompt(String originalQuery, List<String> unmatchedTokens) {
        return """
                원본 검색어: %s
                의미 파악이 필요한 토큰: %s

                위 토큰의 의도를 파악하여 채용공고에서 사용될 관련 키워드를 생성해주세요.
                """.formatted(originalQuery, String.join(", ", unmatchedTokens));
    }

    private List<String> parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        return Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}

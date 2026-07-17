package com.jobai.backend.domain.search.service;

import java.util.List;

/**
 * 쿼리 확장 결과. 확장된 텍스트와 추가된 키워드를 담는다.
 *
 * @param expandedText     임베딩에 사용할 텍스트 (원본 쿼리 + 확장 키워드)
 * @param expandedKeywords 확장으로 추가된 키워드 목록 (SearchInfo 전달용)
 */
public record QueryExpansionResult(
        String expandedText,
        List<String> expandedKeywords
) {
    /** 확장 없이 원본 쿼리를 그대로 반환한다. */
    public static QueryExpansionResult unchanged(String originalQuery) {
        return new QueryExpansionResult(originalQuery, List.of());
    }

    /** 쿼리가 확장되었는지 여부를 반환한다. */
    public boolean wasExpanded() {
        return expandedKeywords != null && !expandedKeywords.isEmpty();
    }
}

package com.jobai.backend.domain.search.dto;

import java.util.List;
import java.util.stream.Stream;

/**
 * 쿼리 확장 결과.
 *
 * <p>unmatched 토큰을 세 가지로 분류하며, 각 분류는 개념 그룹 단위로 표현한다.
 *
 * <ul>
 *   <li>exactRequired   — 공고에 반드시 등장해야 하는 기술 스택·도구명 그룹 (SQL AND)</li>
 *   <li>semanticRequired  — 반드시 충족해야 하는 의미 조건의 유의어 그룹 (SQL AND + 임베딩)</li>
 *   <li>semanticPreferred — 선호 분위기·문화 표현의 유의어 그룹 (임베딩 힌트)</li>
 * </ul>
 *
 * <p>각 그룹 내 terms는 OR, 그룹 간은 AND로 SQL에 적용된다.
 */
public record QueryExpansionResult(
        String expandedText,
        List<String> expandedKeywords,
        List<RequirementGroup> exactRequired,
        List<RequirementGroup> semanticRequired,
        List<RequirementGroup> semanticPreferred
) {
    /** 확장 없이 원본 쿼리를 그대로 반환한다. */
    public static QueryExpansionResult unchanged(String originalQuery) {
        return new QueryExpansionResult(
                originalQuery, List.of(), List.of(), List.of(), List.of());
    }

    /** 쿼리가 확장되었는지 여부를 반환한다. */
    public boolean wasExpanded() {
        return !exactRequired.isEmpty()
                || !semanticRequired.isEmpty() || !semanticPreferred.isEmpty();
    }

    /** 임베딩 텍스트에 추가할 모든 semantic terms (required → preferred 순). */
    public List<String> allSemanticTerms() {
        return Stream.concat(
                semanticRequired.stream().flatMap(g -> g.terms().stream()),
                semanticPreferred.stream().flatMap(g -> g.terms().stream())
        ).distinct().toList();
    }
}

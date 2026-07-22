package com.jobai.backend.domain.search.eval;

import java.util.List;
import java.util.Set;

/**
 * 검색 품질 지표 계산 유틸리티
 *
 * <ul>
 *   <li>MRR@K  : 상위 K위 내 첫 번째 정답의 역수 순위 평균 (1.0에 가까울수록 좋음)</li>
 *   <li>Recall@K: 상위 K위 내에 포함된 정답 비율</li>
 *   <li>P@K    : 상위 K위 중 정답인 비율 (Precision@K)</li>
 * </ul>
 *
 * ranked   — 검색 결과 키 목록 ("PRIVATE:123", "PUBLIC:45" 형식, 순위순)
 * relevant — 정답 키 집합
 */
public final class SearchMetrics {

    private SearchMetrics() {}

    /**
     * MRR@K: 상위 K위 내 첫 번째 정답 위치의 역수.
     * 1등이면 1.0, 2등이면 0.5, K위 내에 없으면 0.0.
     */
    public static double mrr(List<String> ranked, Set<String> relevant, int k) {
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (relevant.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * Recall@K: 상위 K위 내에 포함된 정답 수 / 전체 정답 수.
     * 정답이 없으면 0.0.
     */
    public static double recallAtK(List<String> ranked, Set<String> relevant, int k) {
        if (relevant.isEmpty()) return 0.0;
        long hits = ranked.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / relevant.size();
    }

    /**
     * Precision@K: 상위 K위 중 정답인 비율.
     */
    public static double precisionAtK(List<String> ranked, Set<String> relevant, int k) {
        if (k == 0) return 0.0;
        long hits = ranked.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / k;
    }
}

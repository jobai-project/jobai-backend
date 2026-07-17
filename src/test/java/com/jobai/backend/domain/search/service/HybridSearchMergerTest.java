package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSearchMergerTest {

    private final HybridSearchMerger merger = new HybridSearchMerger();

    @Test
    @DisplayName("양쪽에 모두 있는 문서가 한쪽만 있는 문서보다 높은 RRF 점수")
    void 양쪽등장_문서가_상위() {
        JobSummary both = job(1L, "PRIVATE", "양쪽 등장");
        JobSummary keywordOnly = job(2L, "PRIVATE", "키워드만");
        JobSummary vectorOnly = job(3L, "PRIVATE", "벡터만");

        List<JobSummary> keyword = List.of(keywordOnly, both);    // keywordOnly=1등, both=2등
        List<JobSummary> vector = List.of(both, vectorOnly);      // both=1등, vectorOnly=2등

        List<JobSummary> result = merger.merge(keyword, vector);

        // both는 양쪽 점수 합산 → 최상위
        assertThat(result.get(0).title()).isEqualTo("양쪽 등장");
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("RRF 순위 기반 정렬 — 키워드 1등이 벡터 2등보다 앞")
    void 순위기반_정렬() {
        JobSummary a = job(1L, "PRIVATE", "A");
        JobSummary b = job(2L, "PRIVATE", "B");
        JobSummary c = job(3L, "PRIVATE", "C");

        List<JobSummary> keyword = List.of(a, b, c);
        List<JobSummary> vector = List.of();

        List<JobSummary> result = merger.merge(keyword, vector);

        assertThat(result.stream().map(JobSummary::title).toList())
                .containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("PRIVATE/PUBLIC 동일 id라도 source가 다르면 별도 문서")
    void source별_별도문서() {
        JobSummary prv = job(1L, "PRIVATE", "사기업");
        JobSummary pub = job(1L, "PUBLIC", "공기업");

        List<JobSummary> keyword = List.of(prv);
        List<JobSummary> vector = List.of(pub);

        List<JobSummary> result = merger.merge(keyword, vector);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("양쪽 모두 비어있으면 빈 결과")
    void 양쪽_빈리스트() {
        List<JobSummary> result = merger.merge(List.of(), List.of());
        assertThat(result).isEmpty();
    }

    private static JobSummary job(Long id, String source, String title) {
        return JobSummary.of(id, source, title, "회사", "서울", "백엔드",
                "정규직", "신입", "https://apply.example.com", null,
                LocalDateTime.of(2025, 6, 1, 10, 0), "EXACT");
    }
}

package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.ai.client.AiRerankClient;
import com.jobai.backend.domain.ai.dto.RerankResponse;
import com.jobai.backend.domain.ai.dto.RerankResponse.RerankScore;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SearchRerankerTest {

    private AiRerankClient aiRerankClient;
    private SearchReranker searchReranker;

    @BeforeEach
    void setUp() {
        aiRerankClient = Mockito.mock(AiRerankClient.class);
        searchReranker = new SearchReranker(aiRerankClient);

        ReflectionTestUtils.setField(searchReranker, "enabled", true);
        ReflectionTestUtils.setField(searchReranker, "topN", 50);
    }

    @Test
    @DisplayName("Cross-Encoder 점수 기반으로 재정렬")
    void 재정렬_성공() {
        JobSummary a = job(1L, "PRIVATE", "A");
        JobSummary b = job(2L, "PRIVATE", "B");
        JobSummary c = job(3L, "PRIVATE", "C");

        // B가 가장 높은 점수 → 1등으로 재정렬
        when(aiRerankClient.rerank(any())).thenReturn(Mono.just(new RerankResponse(List.of(
                new RerankScore(2L, "PRIVATE", 0.95),
                new RerankScore(3L, "PRIVATE", 0.80),
                new RerankScore(1L, "PRIVATE", 0.60)
        ))));

        List<JobSummary> result = searchReranker.rerank("재택 백엔드", List.of(a, b, c));

        assertThat(result.stream().map(JobSummary::title).toList())
                .containsExactly("B", "C", "A");
    }

    @Test
    @DisplayName("disabled 상태면 원본 순서 유지")
    void disabled면_원본유지() {
        ReflectionTestUtils.setField(searchReranker, "enabled", false);

        JobSummary a = job(1L, "PRIVATE", "A");
        JobSummary b = job(2L, "PRIVATE", "B");

        List<JobSummary> result = searchReranker.rerank("query", List.of(a, b));

        assertThat(result.stream().map(JobSummary::title).toList())
                .containsExactly("A", "B");
        verifyNoInteractions(aiRerankClient);
    }

    @Test
    @DisplayName("ai-server 실패 시 원본 순서 유지")
    void ai서버_실패시_원본유지() {
        JobSummary a = job(1L, "PRIVATE", "A");

        when(aiRerankClient.rerank(any())).thenReturn(Mono.error(new RuntimeException("ai-server 장애")));

        List<JobSummary> result = searchReranker.rerank("query", List.of(a));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("A");
    }

    @Test
    @DisplayName("빈 후보군이면 rerank 호출하지 않음")
    void 빈후보군_스킵() {
        List<JobSummary> result = searchReranker.rerank("query", List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(aiRerankClient);
    }

    private static JobSummary job(Long id, String source, String title) {
        return JobSummary.of(id, source, title, "회사", "서울", "백엔드",
                "정규직", "신입", "https://apply.example.com", null,
                LocalDateTime.of(2025, 6, 1, 10, 0), "EXACT");
    }
}

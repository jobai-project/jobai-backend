package com.jobai.backend.domain.techcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.techcard.collector.RawArticle;
import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.service.TechCardSummarizeService.CardSummary;
import com.jobai.backend.global.llm.AnthropicClient;
import com.jobai.backend.global.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TechCardSummarizeServiceTest {

    private AnthropicClient anthropicClient;
    private TechCardSummarizeService service;

    @BeforeEach
    void setUp() {
        anthropicClient = Mockito.mock(AnthropicClient.class);
        service = new TechCardSummarizeService(anthropicClient, new ObjectMapper());
    }

    @Test
    @DisplayName("정상 응답: 입력 순서대로 1:1 요약된다")
    void summarizesInOrder() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[{\"headline\": \"제목1\", \"subtext\": \"부연1\"}, {\"headline\": \"제목2\", \"subtext\": \"부연2\"}]");

        List<CardSummary> result = service.summarize(List.of(
                article("hn:1", "Title 1"),
                article("hn:2", "Title 2")
        ));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).headline()).isEqualTo("제목1");
        assertThat(result.get(1).headline()).isEqualTo("제목2");
    }

    @Test
    @DisplayName("응답에 설명이 섞여도 JSON 배열만 추출해 파싱한다")
    void extractsJsonFromNoisyResponse() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("요약 결과입니다: [{\"headline\": \"제목\", \"subtext\": \"부연\"}] 이상입니다.");

        List<CardSummary> result = service.summarize(List.of(article("hn:1", "Title")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).headline()).isEqualTo("제목");
    }

    @Test
    @DisplayName("개수 불일치: 그 배치는 전부 null로 처리된다")
    void countMismatchReturnsNulls() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[{\"headline\": \"제목1\", \"subtext\": \"부연1\"}]");

        List<CardSummary> result = service.summarize(List.of(
                article("hn:1", "Title 1"),
                article("hn:2", "Title 2")
        ));

        assertThat(result).hasSize(2);
        assertThat(result).containsOnlyNulls();
    }

    @Test
    @DisplayName("headline이 비어있으면 해당 항목만 null")
    void emptyHeadlineBecomesNull() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[{\"headline\": \"\", \"subtext\": \"부연1\"}, {\"headline\": \"제목2\", \"subtext\": \"부연2\"}]");

        List<CardSummary> result = service.summarize(List.of(
                article("hn:1", "Title 1"),
                article("hn:2", "Title 2")
        ));

        assertThat(result.get(0)).isNull();
        assertThat(result.get(1)).isNotNull();
    }

    @Test
    @DisplayName("headline이 200자 초과하면 해당 항목만 null")
    void longHeadlineBecomesNull() {
        String longHeadline = "가".repeat(201);
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[{\"headline\": \"%s\", \"subtext\": \"부연\"}]".formatted(longHeadline));

        List<CardSummary> result = service.summarize(List.of(article("hn:1", "Title")));

        assertThat(result.get(0)).isNull();
    }

    @Test
    @DisplayName("깨진 JSON: 그 배치는 전부 null")
    void brokenJsonReturnsNulls() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[{\"headline\": \"제목\"");

        List<CardSummary> result = service.summarize(List.of(article("hn:1", "Title")));

        assertThat(result).hasSize(1);
        assertThat(result).containsOnlyNulls();
    }

    @Test
    @DisplayName("LLM 호출 실패: 그 배치는 전부 null")
    void llmFailureReturnsNulls() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("Anthropic 호출 실패: 401"));

        List<CardSummary> result = service.summarize(List.of(article("hn:1", "Title")));

        assertThat(result).hasSize(1);
        assertThat(result).containsOnlyNulls();
    }

    @Test
    @DisplayName("null 입력: 빈 리스트 반환, LLM 호출하지 않는다")
    void nullInputReturnsEmpty() {
        List<CardSummary> result = service.summarize(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(anthropicClient);
    }

    @Test
    @DisplayName("빈 목록 입력: 빈 리스트 반환, LLM 호출하지 않는다")
    void emptyInputReturnsEmpty() {
        List<CardSummary> result = service.summarize(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(anthropicClient);
    }

    @Test
    @DisplayName("여러 배치 중 한 배치만 실패하면 그 배치만 null, 나머지는 정상")
    void partialBatchFailureIsolated() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("실패"))
                .thenReturn("[{\"headline\": \"제목\", \"subtext\": \"부연\"}]");

        List<RawArticle> articles = new ArrayList<>();
        for (int i = 0; i < 10; i++) articles.add(article("hn:" + i, "Title " + i));
        articles.add(article("hn:10", "Title 10"));

        List<CardSummary> result = service.summarize(articles);

        assertThat(result).hasSize(11);
        assertThat(result.subList(0, 10)).containsOnlyNulls();
        assertThat(result.get(10)).isNotNull();
    }

    private RawArticle article(String externalId, String title) {
        return new RawArticle(ContentSource.HACKERNEWS, externalId, title,
                "https://example.com", "", null);
    }
}

package com.jobai.backend.domain.techcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.techcard.collector.RawArticle;
import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.service.TechCardSummarizeService.PickedSummary;
import com.jobai.backend.global.llm.AnthropicClient;
import com.jobai.backend.global.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    @DisplayName("정상 응답: LLM이 선택한 기사의 headline/subtext가 반환된다")
    void picksAndSummarizes() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"pickedIndex\": 2, \"headline\": \"Go 언어 인기가 심상치 않아요\", \"subtext\": \"백엔드 공고에서 Go 언급이 늘고 있어요\"}");

        List<RawArticle> articles = List.of(
                article("hn:1", "Some AI Tool Released"),
                article("hn:2", "Go Language Popularity Surges"),
                article("hn:3", "New CSS Feature")
        );

        PickedSummary result = service.pickAndSummarize(articles);

        assertThat(result).isNotNull();
        assertThat(result.pickedIndex()).isEqualTo(1); // pickedIndex 2 → 0-based 1
        assertThat(result.headline()).isEqualTo("Go 언어 인기가 심상치 않아요");
        assertThat(result.subtext()).isEqualTo("백엔드 공고에서 Go 언급이 늘고 있어요");
    }

    @Test
    @DisplayName("응답에 설명이 섞여도 JSON 객체만 추출해 파싱한다")
    void extractsJsonFromNoisyResponse() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("선별 결과입니다: {\"pickedIndex\": 1, \"headline\": \"제목\", \"subtext\": \"부연\"} 이상입니다.");

        PickedSummary result = service.pickAndSummarize(List.of(article("hn:1", "Title")));

        assertThat(result).isNotNull();
        assertThat(result.headline()).isEqualTo("제목");
    }

    @Test
    @DisplayName("pickedIndex가 범위를 벗어나면 null 반환")
    void invalidPickedIndexReturnsNull() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"pickedIndex\": 10, \"headline\": \"제목\", \"subtext\": \"부연\"}");

        PickedSummary result = service.pickAndSummarize(List.of(
                article("hn:1", "Title1"),
                article("hn:2", "Title2")
        ));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("pickedIndex가 0이면 null 반환")
    void zeroPickedIndexReturnsNull() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"pickedIndex\": 0, \"headline\": \"제목\", \"subtext\": \"부연\"}");

        PickedSummary result = service.pickAndSummarize(List.of(article("hn:1", "Title")));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("headline이 비어있으면 null 반환")
    void emptyHeadlineReturnsNull() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"pickedIndex\": 1, \"headline\": \"\", \"subtext\": \"부연\"}");

        PickedSummary result = service.pickAndSummarize(List.of(article("hn:1", "Title")));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("subtext가 비어있으면 null 반환")
    void emptySubtextReturnsNull() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"pickedIndex\": 1, \"headline\": \"제목\", \"subtext\": \"\"}");

        PickedSummary result = service.pickAndSummarize(List.of(article("hn:1", "Title")));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("깨진 JSON: null 반환")
    void brokenJsonReturnsNull() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"pickedIndex\": 1, \"headline\":");

        PickedSummary result = service.pickAndSummarize(List.of(article("hn:1", "Title")));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("LLM 호출 실패: null 반환")
    void llmFailureReturnsNull() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("Anthropic 호출 실패: 401"));

        PickedSummary result = service.pickAndSummarize(List.of(article("hn:1", "Title")));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("null 입력: null 반환, LLM 호출하지 않는다")
    void nullInputReturnsNull() {
        PickedSummary result = service.pickAndSummarize(null);

        assertThat(result).isNull();
        verifyNoInteractions(anthropicClient);
    }

    @Test
    @DisplayName("빈 목록 입력: null 반환, LLM 호출하지 않는다")
    void emptyInputReturnsNull() {
        PickedSummary result = service.pickAndSummarize(List.of());

        assertThat(result).isNull();
        verifyNoInteractions(anthropicClient);
    }

    private RawArticle article(String externalId, String title) {
        return new RawArticle(ContentSource.HACKERNEWS, externalId, title,
                "https://example.com", "", null);
    }
}

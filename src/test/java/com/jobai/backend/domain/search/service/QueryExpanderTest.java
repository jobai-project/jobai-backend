package com.jobai.backend.domain.search.service;

import com.jobai.backend.global.llm.AnthropicClient;
import com.jobai.backend.global.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryExpanderTest {

    private AnthropicClient anthropicClient;
    private QueryExpander queryExpander;

    @BeforeEach
    void setUp() throws Exception {
        anthropicClient = Mockito.mock(AnthropicClient.class);
        queryExpander = new QueryExpander(anthropicClient);

        var field = QueryExpander.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.setBoolean(queryExpander, true);
    }

    @Test
    @DisplayName("unmatched 토큰이 있으면 LLM으로 확장 키워드 생성")
    void 쿼리확장_성공() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("원격근무, 리모트워크, 재택, WFH");

        QueryExpansionResult result = queryExpander.expand(
                "재택근무 가능한 백엔드", List.of("재택근무", "가능한"));

        assertThat(result.wasExpanded()).isTrue();
        assertThat(result.expandedKeywords()).containsExactly("원격근무", "리모트워크", "재택", "WFH");
        assertThat(result.expandedText()).contains("재택근무 가능한 백엔드");
        assertThat(result.expandedText()).contains("원격근무");
    }

    @Test
    @DisplayName("unmatched 토큰이 비어있으면 확장하지 않음")
    void unmatched_비어있으면_스킵() {
        QueryExpansionResult result = queryExpander.expand("서울 백엔드", List.of());

        assertThat(result.wasExpanded()).isFalse();
        assertThat(result.expandedText()).isEqualTo("서울 백엔드");
        verifyNoInteractions(anthropicClient);
    }

    @Test
    @DisplayName("disabled 상태면 확장하지 않음")
    void disabled면_스킵() throws Exception {
        var field = QueryExpander.class.getDeclaredField("enabled");
        field.setAccessible(true);
        field.setBoolean(queryExpander, false);

        QueryExpansionResult result = queryExpander.expand(
                "재택근무 백엔드", List.of("재택근무"));

        assertThat(result.wasExpanded()).isFalse();
        verifyNoInteractions(anthropicClient);
    }

    @Test
    @DisplayName("LLM 호출 실패 시 원본 쿼리 반환")
    void LLM실패시_원본반환() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("API 호출 실패"));

        QueryExpansionResult result = queryExpander.expand(
                "재택근무 백엔드", List.of("재택근무"));

        assertThat(result.wasExpanded()).isFalse();
        assertThat(result.expandedText()).isEqualTo("재택근무 백엔드");
    }

    @Test
    @DisplayName("LLM이 빈 응답 반환 시 원본 쿼리 반환")
    void LLM_빈응답시_원본반환() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("");

        QueryExpansionResult result = queryExpander.expand(
                "재택근무 백엔드", List.of("재택근무"));

        assertThat(result.wasExpanded()).isFalse();
    }
}

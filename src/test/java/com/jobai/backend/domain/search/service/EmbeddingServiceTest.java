package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.ai.client.AiEmbeddingClient;
import com.jobai.backend.domain.ai.dto.EmbedResponse;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.DoubleStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    private AiEmbeddingClient aiEmbeddingClient;
    private JobEmbeddingRepository jobEmbeddingRepository;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() throws Exception {
        aiEmbeddingClient = Mockito.mock(AiEmbeddingClient.class);
        jobEmbeddingRepository = Mockito.mock(JobEmbeddingRepository.class);
        embeddingService = new EmbeddingService(aiEmbeddingClient, jobEmbeddingRepository);

        // maxTextLength 필드를 리플렉션으로 설정 (기본값 8000)
        var field = EmbeddingService.class.getDeclaredField("maxTextLength");
        field.setAccessible(true);
        field.setInt(embeddingService, 8000);
    }

    // --- buildText 테스트 ---

    @Test
    @DisplayName("buildText: title과 body를 줄바꿈으로 결합한다")
    void buildText_결합() {
        String result = embeddingService.buildText("백엔드 개발자", "Spring Boot 경험 필수");
        assertThat(result).isEqualTo("백엔드 개발자\nSpring Boot 경험 필수");
    }

    @Test
    @DisplayName("buildText: title만 있으면 title만 반환한다")
    void buildText_title만() {
        String result = embeddingService.buildText("프론트엔드 개발자", null);
        assertThat(result).isEqualTo("프론트엔드 개발자");
    }

    @Test
    @DisplayName("buildText: body만 있으면 body만 반환한다")
    void buildText_body만() {
        String result = embeddingService.buildText(null, "React 경험자 우대");
        assertThat(result).isEqualTo("React 경험자 우대");
    }

    @Test
    @DisplayName("buildText: 빈 문자열은 무시한다")
    void buildText_빈문자열() {
        String result = embeddingService.buildText("  ", "본문 내용");
        assertThat(result).isEqualTo("본문 내용");
    }

    @Test
    @DisplayName("buildText: 최대 길이를 초과하면 잘린다")
    void buildText_길이제한() throws Exception {
        var field = EmbeddingService.class.getDeclaredField("maxTextLength");
        field.setAccessible(true);
        field.setInt(embeddingService, 10);

        String result = embeddingService.buildText("12345", "67890ABCDE");
        assertThat(result).hasSize(10);
        assertThat(result).isEqualTo("12345\n6789");
    }

    // --- stripHtml 테스트 ---

    @Test
    @DisplayName("stripHtml: HTML 태그를 제거하고 텍스트만 반환한다")
    void stripHtml_태그제거() {
        String html = "<div><h1>채용공고</h1><p>Python <strong>필수</strong></p></div>";
        String result = embeddingService.stripHtml(html);
        assertThat(result).isEqualTo("채용공고 Python 필수");
    }

    @Test
    @DisplayName("stripHtml: null 입력 시 빈 문자열 반환")
    void stripHtml_null() {
        assertThat(embeddingService.stripHtml(null)).isEmpty();
    }

    @Test
    @DisplayName("stripHtml: 빈 문자열 입력 시 빈 문자열 반환")
    void stripHtml_빈문자열() {
        assertThat(embeddingService.stripHtml("   ")).isEmpty();
    }

    @Test
    @DisplayName("stripHtml: 태그가 없는 일반 텍스트는 그대로 반환")
    void stripHtml_일반텍스트() {
        String result = embeddingService.stripHtml("일반 텍스트입니다");
        assertThat(result).isEqualTo("일반 텍스트입니다");
    }

    // --- embedQuery 테스트 ---

    @Test
    @DisplayName("embedQuery: ai-server 응답을 float 배열로 변환한다")
    void embedQuery_정상() {
        List<Double> vector = DoubleStream.generate(() -> 0.1)
                .limit(768).boxed().toList();
        when(aiEmbeddingClient.embed(any()))
                .thenReturn(Mono.just(new EmbedResponse(vector)));

        float[] result = embeddingService.embedQuery("Python 데이터 파이프라인");

        assertThat(result).hasSize(768);
        assertThat(result[0]).isEqualTo(0.1f);
    }

    @Test
    @DisplayName("embedQuery: 차원이 768이 아니면 예외를 던진다")
    void embedQuery_차원불일치() {
        List<Double> vector = List.of(0.1, 0.2, 0.3);
        when(aiEmbeddingClient.embed(any()))
                .thenReturn(Mono.just(new EmbedResponse(vector)));

        assertThatThrownBy(() -> embeddingService.embedQuery("test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("차원이 올바르지 않습니다");
    }

    @Test
    @DisplayName("embedQuery: 빈 벡터 응답 시 예외를 던진다")
    void embedQuery_빈응답() {
        when(aiEmbeddingClient.embed(any()))
                .thenReturn(Mono.just(new EmbedResponse(List.of())));

        assertThatThrownBy(() -> embeddingService.embedQuery("test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("embedQuery: null 벡터 응답 시 예외를 던진다")
    void embedQuery_null응답() {
        when(aiEmbeddingClient.embed(any()))
                .thenReturn(Mono.just(new EmbedResponse(null)));

        assertThatThrownBy(() -> embeddingService.embedQuery("test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("embedQuery: ai-server 오류는 그대로 전파한다")
    void embedQuery_ai서버오류() {
        when(aiEmbeddingClient.embed(any()))
                .thenReturn(Mono.error(new RuntimeException("ai-server 연결 실패")));

        assertThatThrownBy(() -> embeddingService.embedQuery("test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ai-server 연결 실패");
    }

    // --- embedResumeText 테스트 ---

    @Test
    @DisplayName("embedResumeText: 이력서 텍스트를 768차원 벡터로 변환한다")
    void embedResumeText_정상() {
        List<Double> vector = DoubleStream.generate(() -> 0.5)
                .limit(768).boxed().toList();
        when(aiEmbeddingClient.embed(any()))
                .thenReturn(Mono.just(new EmbedResponse(vector)));

        float[] result = embeddingService.embedResumeText("Java Spring Boot 경험 3년");

        assertThat(result).hasSize(768);
        assertThat(result[0]).isEqualTo(0.5f);
    }

    @Test
    @DisplayName("embedResumeText: 최대 길이를 초과하면 잘라서 전송한다")
    void embedResumeText_길이제한() throws Exception {
        var field = EmbeddingService.class.getDeclaredField("maxTextLength");
        field.setAccessible(true);
        field.setInt(embeddingService, 10);

        List<Double> vector = DoubleStream.generate(() -> 0.1)
                .limit(768).boxed().toList();
        when(aiEmbeddingClient.embed(any()))
                .thenReturn(Mono.just(new EmbedResponse(vector)));

        float[] result = embeddingService.embedResumeText("12345678901234567890");

        assertThat(result).hasSize(768);
    }

    @Test
    @DisplayName("embedResumeText: ai-server 오류는 그대로 전파한다")
    void embedResumeText_ai서버오류() {
        when(aiEmbeddingClient.embed(any()))
                .thenReturn(Mono.error(new RuntimeException("ai-server 연결 실패")));

        assertThatThrownBy(() -> embeddingService.embedResumeText("이력서 텍스트"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ai-server 연결 실패");
    }
}

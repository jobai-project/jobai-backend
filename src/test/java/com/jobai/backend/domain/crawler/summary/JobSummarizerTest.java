package com.jobai.backend.domain.crawler.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.summary.service.JobSummarizer;
import com.jobai.backend.domain.summary.service.JobSummaryParseException;
import com.jobai.backend.global.llm.AnthropicClient;
import com.jobai.backend.global.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class JobSummarizerTest {

    private AnthropicClient anthropicClient;
    private JobSummarizer summarizer;

    @BeforeEach
    void setUp() {
        anthropicClient = Mockito.mock(AnthropicClient.class);
        summarizer = new JobSummarizer(anthropicClient, new ObjectMapper());
    }

    @Test
    @DisplayName("정상 응답: 4개 필드가 포함된 JSON을 반환한다")
    void summarizesSuccessfully() {
        String llmResponse = """
                {
                  "techStack": ["Java", "Spring Boot"],
                  "responsibilities": ["백엔드 API 개발"],
                  "qualifications": ["Java 3년 이상"],
                  "preferredQualifications": ["MSA 경험"]
                }
                """;
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn(llmResponse);

        String result = summarizer.summarize("공고 본문 텍스트");

        assertThat(result).contains("techStack");
        assertThat(result).contains("responsibilities");
        assertThat(result).contains("qualifications");
        assertThat(result).contains("preferredQualifications");
    }

    @Test
    @DisplayName("노이즈 포함 응답: JSON 객체만 추출하여 파싱한다")
    void extractsJsonFromNoisyResponse() {
        String llmResponse = """
                다음은 요약 결과입니다:
                {
                  "techStack": ["Python"],
                  "responsibilities": ["데이터 분석"],
                  "qualifications": ["Python 2년"],
                  "preferredQualifications": []
                }
                위 내용을 참고하세요.
                """;
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn(llmResponse);

        String result = summarizer.summarize("공고 본문");

        assertThat(result).startsWith("{");
        assertThat(result).endsWith("}");
        assertThat(result).contains("Python");
    }

    @Test
    @DisplayName("필수 필드 누락: JobSummaryParseException을 던진다")
    void throwsOnMissingField() {
        String llmResponse = """
                {
                  "techStack": ["Java"],
                  "responsibilities": ["개발"]
                }
                """;
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn(llmResponse);

        assertThatThrownBy(() -> summarizer.summarize("공고 본문"))
                .isInstanceOf(JobSummaryParseException.class)
                .hasMessageContaining("필수 필드 누락");
    }

    @Test
    @DisplayName("필드가 배열이 아닌 경우: JobSummaryParseException을 던진다")
    void throwsOnNonArrayField() {
        String llmResponse = """
                {
                  "techStack": "Java",
                  "responsibilities": ["개발"],
                  "qualifications": ["자격"],
                  "preferredQualifications": ["우대"]
                }
                """;
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn(llmResponse);

        assertThatThrownBy(() -> summarizer.summarize("공고 본문"))
                .isInstanceOf(JobSummaryParseException.class)
                .hasMessageContaining("배열이 아닙니다");
    }

    @Test
    @DisplayName("LLM 호출 실패: LlmException이 그대로 전파된다")
    void llmExceptionPropagates() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("Anthropic 호출 실패: 401"));

        assertThatThrownBy(() -> summarizer.summarize("공고 본문"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("401");
    }
}

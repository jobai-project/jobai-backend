package com.jobai.backend.domain.ai.client;

import com.jobai.backend.global.ai.client.AiScoringClient;
import com.jobai.backend.global.ai.dto.ScorePrivateRequest;
import com.jobai.backend.global.ai.dto.ScorePrivateResponse;
import com.jobai.backend.global.ai.exception.AiClientException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiScoringClientTest {

    private MockWebServer mockWebServer;
    private AiScoringClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        client = new AiScoringClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("정상 응답을 ScorePrivateResponse로 역직렬화한다")
    void scorePrivate_정상() throws Exception {
        String body = """
                {
                  "score": 85.5,
                  "above_threshold": true,
                  "matched_skills": ["Java", "Spring"],
                  "missing_skills": ["Kubernetes"],
                  "career_met": true,
                  "score_reason": "기술스택 일치도 높음",
                  "model_version": "v1.0"
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(body)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        ScorePrivateRequest request = new ScorePrivateRequest(
                "백엔드 개발자", List.of(0.1, 0.2), List.of(0.3, 0.4),
                List.of("Java", "Spring"), 3);

        ScorePrivateResponse response = client.scorePrivate(request).block();

        assertThat(response).isNotNull();
        assertThat(response.score()).isEqualTo(85.5);
        assertThat(response.aboveThreshold()).isTrue();
        assertThat(response.matchedSkills()).containsExactly("Java", "Spring");
        assertThat(response.missingSkills()).containsExactly("Kubernetes");
        assertThat(response.careerMet()).isTrue();
        assertThat(response.scoreReason()).isEqualTo("기술스택 일치도 높음");
        assertThat(response.modelVersion()).isEqualTo("v1.0");

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/score/private");
    }

    @Test
    @DisplayName("5xx 응답 시 AiClientException을 던진다")
    void scorePrivate_서버오류() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE));

        ScorePrivateRequest request = new ScorePrivateRequest(
                "테스트", List.of(0.1), List.of(0.2), List.of("Java"), 0);

        assertThatThrownBy(() -> client.scorePrivate(request).block())
                .isInstanceOf(AiClientException.class);
    }

    @Test
    @DisplayName("4xx 응답 시 AiClientException을 던진다")
    void scorePrivate_클라이언트오류() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(422)
                .setBody("Validation Error")
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE));

        ScorePrivateRequest request = new ScorePrivateRequest(
                "테스트", List.of(0.1), List.of(0.2), List.of("Java"), 0);

        assertThatThrownBy(() -> client.scorePrivate(request).block())
                .isInstanceOf(AiClientException.class);
    }
}

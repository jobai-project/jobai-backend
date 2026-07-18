package com.jobai.backend.domain.ai.client;

import com.jobai.backend.global.ai.client.AiEmbeddingClient;
import com.jobai.backend.global.ai.dto.EmbedRequest;
import com.jobai.backend.global.ai.dto.EmbedResponse;
import com.jobai.backend.global.ai.exception.AiClientException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEmbeddingClientTest {

    private MockWebServer server;
    private AiEmbeddingClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .build();
        client = new AiEmbeddingClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String vectorJson768() {
        StringBuilder sb = new StringBuilder("{\"vector\":[");
        for (int i = 0; i < 768; i++) {
            if (i > 0) sb.append(",");
            sb.append("0.1");
        }
        sb.append("]}");
        return sb.toString();
    }

    @Test
    @DisplayName("embedJd: /embed/jd 를 호출하고 벡터를 반환한다")
    void embedJd_정상응답() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody(vectorJson768()));

        EmbedResponse response = client.embedJd(new EmbedRequest("FastAPI 기반 REST API 서버 개발 경험")).block();

        assertThat(response).isNotNull();
        assertThat(response.vector()).hasSize(768);

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/embed/jd");
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getBody().readUtf8())
                .isEqualTo("{\"text\":\"FastAPI 기반 REST API 서버 개발 경험\"}");
    }

    @Test
    @DisplayName("embed: embedJd와 동일하게 /embed/jd 를 호출한다")
    void embed_embedJd로위임() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody(vectorJson768()));

        EmbedResponse response = client.embed(new EmbedRequest("백엔드 개발자 JD")).block();

        assertThat(response.vector()).hasSize(768);
        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded.getPath()).isEqualTo("/embed/jd");
    }

    @Test
    @DisplayName("embedNcs: /embed/ncs 를 호출하고 벡터를 반환한다")
    void embedNcs_정상응답() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody(vectorJson768()));

        EmbedResponse response = client.embedNcs(new EmbedRequest("정보보호 관련 기술, 침해대응 및 취약점 분석")).block();

        assertThat(response.vector()).hasSize(768);
        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded.getPath()).isEqualTo("/embed/ncs");
        assertThat(recorded.getBody().readUtf8())
                .isEqualTo("{\"text\":\"정보보호 관련 기술, 침해대응 및 취약점 분석\"}");
    }

    @Test
    @DisplayName("HTTP 400: AiClientException 을 status/body 와 함께 던진다")
    void embedJd_400에러() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"detail\":\"text too long\"}"));

        assertThatThrownBy(() -> client.embedJd(new EmbedRequest("text")).block())
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> {
                    AiClientException ex = (AiClientException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getResponseBody()).contains("text too long");
                });
    }

    @Test
    @DisplayName("HTTP 500: AiClientException 을 status/body 와 함께 던진다")
    void embedNcs_500에러() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("internal error"));

        assertThatThrownBy(() -> client.embedNcs(new EmbedRequest("text")).block())
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> {
                    AiClientException ex = (AiClientException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(500);
                    assertThat(ex.getResponseBody()).isEqualTo("internal error");
                });
    }

    @Test
    @DisplayName("빈 에러 바디도 AiClientException 으로 정상 변환된다")
    void embedJd_빈에러바디() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client.embedJd(new EmbedRequest("text")).block())
                .isInstanceOf(AiClientException.class)
                .satisfies(e -> {
                    AiClientException ex = (AiClientException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(503);
                    assertThat(ex.getResponseBody()).isEmpty();
                });
    }
}

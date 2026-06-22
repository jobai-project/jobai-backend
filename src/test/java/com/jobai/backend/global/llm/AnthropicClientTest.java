package com.jobai.backend.global.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicClientTest {

    private MockWebServer server;
    private AnthropicClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // base-url 을 mock 서버로 향하게 주입
        client = new AnthropicClient(
                new ObjectMapper(),
                "test-key",
                "claude-haiku-4-5-20251001",
                server.url("/").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("정상 응답: content[0].text 를 추출한다")
    void extractsText() {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody("""
                    {"content":[{"type":"text","text":"[\\"백엔드\\"]"}]}
                    """));

        String result = client.complete("system", "user", 100);

        assertThat(result).isEqualTo("[\"백엔드\"]");
    }

    @Test
    @DisplayName("HTTP 401: LlmException 을 던진다")
    void throwsOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\":\"unauthorized\"}"));

        assertThatThrownBy(() -> client.complete("system", "user", 100))
                .isInstanceOf(LlmException.class);
    }

    @Test
    @DisplayName("content 없는 응답: LlmException 을 던진다")
    void throwsWhenNoContent() {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody("{\"content\":[]}"));

        assertThatThrownBy(() -> client.complete("system", "user", 100))
                .isInstanceOf(LlmException.class);
    }

    @Test
    @DisplayName("요청에 필수 헤더(x-api-key, anthropic-version)가 실린다")
    void sendsRequiredHeaders() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));

        client.complete("system", "user", 100);

        var recorded = server.takeRequest();
        assertThat(recorded.getHeader("x-api-key")).isEqualTo("test-key");
        assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(recorded.getPath()).isEqualTo("/v1/messages");
    }
}

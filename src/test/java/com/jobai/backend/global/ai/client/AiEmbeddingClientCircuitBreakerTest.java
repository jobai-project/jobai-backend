package com.jobai.backend.global.ai.client;

import com.jobai.backend.global.ai.dto.EmbedRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEmbeddingClientCircuitBreakerTest {

    private MockWebServer server;
    private AiEmbeddingClient client;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .build();

        // 테스트용 서킷 설정: 빠른 전환을 위해 작은 값 사용
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        circuitBreaker = CircuitBreaker.of("ai-server-test", config);
        client = new AiEmbeddingClient(webClient, circuitBreaker, new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String vectorJson() {
        StringBuilder sb = new StringBuilder("{\"vector\":[");
        for (int i = 0; i < 768; i++) {
            if (i > 0) sb.append(",");
            sb.append("0.1");
        }
        sb.append("]}");
        return sb.toString();
    }

    @Test
    @DisplayName("정상 응답이 계속되면 서킷은 CLOSED 상태를 유지한다")
    void circuitStaysClosedOnSuccess() {
        for (int i = 0; i < 10; i++) {
            server.enqueue(new MockResponse()
                    .setHeader("content-type", "application/json")
                    .setBody(vectorJson()));
        }

        for (int i = 0; i < 10; i++) {
            client.embedJd(new EmbedRequest("test")).block();
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(server.getRequestCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("연속 실패 시 서킷이 OPEN되고 이후 요청은 서버 호출 없이 즉시 실패한다")
    void circuitOpensAfterFailureThreshold() {
        // 5건 연속 500 응답 → 실패율 100% → 서킷 OPEN
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        }

        for (int i = 0; i < 5; i++) {
            try {
                client.embedJd(new EmbedRequest("test")).block();
            } catch (Exception ignored) {
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 서킷 OPEN 후 요청 → 서버 호출 없이 CallNotPermittedException
        assertThatThrownBy(() -> client.embedJd(new EmbedRequest("test")).block())
                .isInstanceOf(CallNotPermittedException.class);

        // MockWebServer에 6번째 요청이 도달하지 않았음을 검증
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("서킷 OPEN → 대기 → HALF-OPEN → 성공 시 CLOSED로 복귀한다")
    void circuitRecoversThroughHalfOpen() throws InterruptedException {
        // 5건 실패 → 서킷 OPEN
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        }
        for (int i = 0; i < 5; i++) {
            try {
                client.embedJd(new EmbedRequest("test")).block();
            } catch (Exception ignored) {
            }
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // waitDurationInOpenState(1초) 대기 → HALF-OPEN 전환
        Thread.sleep(1200);

        // HALF-OPEN에서 시험 요청 2건 (permittedNumberOfCallsInHalfOpenState=2)
        for (int i = 0; i < 2; i++) {
            server.enqueue(new MockResponse()
                    .setHeader("content-type", "application/json")
                    .setBody(vectorJson()));
        }

        client.embedJd(new EmbedRequest("test")).block();
        client.embedJd(new EmbedRequest("test")).block();

        // 시험 요청 성공 → CLOSED 복귀
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}

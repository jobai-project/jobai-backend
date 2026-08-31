package com.jobai.backend.global.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 저수준 호출기. 텍스트 프롬프트 → 텍스트 응답.
 * 프롬프트 구성·응답 파싱 같은 도메인 로직은 호출하는 쪽(JobClassifier 등)이 담당한다.
 */
@Slf4j
@Component
public class AnthropicClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final CircuitBreaker circuitBreaker;
    private final Timer successTimer;
    private final Timer failureTimer;

    public AnthropicClient(ObjectMapper objectMapper,
                           @Value("${spring.ai.anthropic.api-key}") String apiKey,
                           @Value("${spring.ai.anthropic.model}") String model,
                           @Value("${spring.ai.anthropic.base-url}") String baseUrl,
                           @Qualifier("anthropicApiCircuitBreaker") CircuitBreaker circuitBreaker,
                           MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.circuitBreaker = circuitBreaker;
        this.successTimer = Timer.builder("llm.anthropic.duration")
                .tag("outcome", "success")
                .description("Anthropic API 호출 소요시간")
                .register(meterRegistry);
        this.failureTimer = Timer.builder("llm.anthropic.duration")
                .tag("outcome", "failure")
                .description("Anthropic API 호출 소요시간")
                .register(meterRegistry);
    }

    /**
     * 시스템 프롬프트 + 유저 메시지를 보내고 모델의 텍스트 응답을 받는다.
     *
     * @param system    시스템 프롬프트(역할·규칙)
     * @param userText   유저 메시지(분류할 제목 목록 등)
     * @param maxTokens  응답 최대 토큰
     * @return 모델이 생성한 텍스트(첫 text 블록)
     */
    public String complete(String system, String userText, int maxTokens) {
        try {
            return circuitBreaker.executeCheckedSupplier(
                    () -> doComplete(system, userText, maxTokens));
        } catch (CallNotPermittedException e) {
            throw new LlmException("Anthropic API 서킷 브레이커 OPEN — 호출 차단됨", e);
        } catch (LlmException e) {
            throw e;
        } catch (Throwable e) {
            throw new LlmException("Anthropic 호출 실패: " + e.getMessage(), e);
        }
    }

    private String doComplete(String system, String userText, int maxTokens) {
        Timer.Sample sample = Timer.start();
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", system,
                "messages", List.of(
                        Map.of("role", "user", "content", userText)
                )
        );

        try {
            String raw = webClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            String result = extractText(raw);
            sample.stop(successTimer);
            return result;
        } catch (Exception e) {
            sample.stop(failureTimer);
            throw new LlmException("Anthropic 호출 실패: " + e.getMessage(), e);
        }
    }

    /** 응답 JSON 에서 content[0].text 를 꺼낸다. */
    private String extractText(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                return content.get(0).path("text").asText("");
            }
            throw new LlmException("응답에 content 가 없음: " + rawJson);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("응답 파싱 실패: " + e.getMessage(), e);
        }
    }


}
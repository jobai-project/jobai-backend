package com.jobai.backend.global.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

    public AnthropicClient(ObjectMapper objectMapper,
                           @Value("${spring.ai.anthropic.api-key}") String apiKey,
                           @Value("${spring.ai.anthropic.model}") String model,
                           @Value("${spring.ai.anthropic.base-url}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

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

            return extractText(raw);
        } catch (Exception e) {
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
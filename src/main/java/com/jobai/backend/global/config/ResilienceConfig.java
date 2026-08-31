package com.jobai.backend.global.config;

import com.jobai.backend.global.ai.exception.AiClientException;
import com.jobai.backend.global.llm.LlmException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        // AI 서버 (임베딩 / 스코어링 / 리랭킹) — 같은 FastAPI 인스턴스
        CircuitBreakerConfig aiServerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(8))   // 타임아웃(10s)의 80% — 메트릭 수집 후 p95 기반으로 튜닝
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(AiClientException.class, TimeoutException.class)
                .build();

        // 공공데이터 API (data.go.kr)
        CircuitBreakerConfig publicDataApiConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(12))  // 타임아웃(15s)의 80% — 메트릭 수집 후 p95 기반으로 튜닝
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        // Anthropic Claude API
        CircuitBreakerConfig anthropicApiConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(25))  // 타임아웃(30s)의 83% — 메트릭 수집 후 p95 기반으로 튜닝
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(LlmException.class, TimeoutException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("ai-server", aiServerConfig);
        registry.circuitBreaker("public-data-api", publicDataApiConfig);
        registry.circuitBreaker("anthropic-api", anthropicApiConfig);

        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)
                .bindTo(meterRegistry);

        return registry;
    }

    @Bean
    public CircuitBreaker aiServerCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("ai-server");
    }

    @Bean
    public CircuitBreaker publicDataApiCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("public-data-api");
    }

    @Bean
    public CircuitBreaker anthropicApiCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("anthropic-api");
    }
}

package com.jobai.backend.global.config;

import com.jobai.backend.global.ai.exception.AiClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 외부 서비스 장애 격리를 위한 Resilience4j 서킷 브레이커 설정.
 *
 * <p>3개의 서킷 브레이커를 정의한다:
 * <ul>
 *   <li>{@code ai-server} — AI 서버(임베딩·스코어링·리랭킹). 같은 FastAPI 인스턴스를 공유하므로 단일 서킷으로 관리.</li>
 *   <li>{@code public-data-api} — 공공데이터 API(data.go.kr). 매시간 배치 호출.</li>
 *   <li>{@code anthropic-api} — Anthropic Claude API. LLM 분류 호출.</li>
 * </ul>
 *
 * <p>서킷이 OPEN되면 호출을 즉시 차단하여 타임아웃 대기 시간을 제거하고,
 * 미처리 건은 기존 배치 로직(EmbeddingBatchService, Kafka DLT 등)이 다음 실행에서 재처리한다.
 *
 * <p>{@code slowCallDurationThreshold}는 타임아웃의 80% 초기값이며,
 * 운영 메트릭(ai.embedding.duration 등) 수집 후 p95 기반으로 튜닝한다.
 */
@Configuration
public class ResilienceConfig {

    /**
     * 서킷 브레이커 레지스트리를 생성하고 Prometheus 메트릭에 바인딩한다.
     *
     * <p>등록되는 메트릭: {@code resilience4j_circuitbreaker_state},
     * {@code resilience4j_circuitbreaker_calls_seconds_count},
     * {@code resilience4j_circuitbreaker_failure_rate}
     */
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
                .recordException(e -> {
                    if (e instanceof AiClientException ace) {
                        return ace.getStatus().is5xxServerError(); // 5xx만 서킷 실패로 기록, 4xx는 클라이언트 오류이므로 제외
                    }
                    return true; // 타임아웃, 연결 거부 등 전송 오류는 모두 실패로 기록
                })
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
                .recordException(e -> true) // LlmException, 타임아웃, 전송 오류 등 모든 예외를 실패로 기록
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

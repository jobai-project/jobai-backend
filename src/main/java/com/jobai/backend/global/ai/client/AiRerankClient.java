package com.jobai.backend.global.ai.client;

import com.jobai.backend.global.ai.dto.RerankRequest;
import com.jobai.backend.global.ai.dto.RerankResponse;
import com.jobai.backend.global.ai.exception.AiClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * ai-server의 {@code POST /rerank} 엔드포인트를 호출하는 클라이언트.
 * Cross-Encoder 모델을 사용하여 검색 결과를 쿼리 의도에 맞게 재정렬한다.
 */
@Component
public class AiRerankClient {

    private final WebClient aiWebClient;
    private final CircuitBreaker circuitBreaker;
    private final Timer successTimer;
    private final Timer failureTimer;

    public AiRerankClient(@Qualifier("aiWebClient") WebClient aiWebClient,
                          @Qualifier("aiServerCircuitBreaker") CircuitBreaker circuitBreaker,
                          MeterRegistry meterRegistry) {
        this.aiWebClient = aiWebClient;
        this.circuitBreaker = circuitBreaker;
        this.successTimer = Timer.builder("ai.rerank.duration")
                .tag("outcome", "success")
                .description("AI 리랭킹 호출 소요시간")
                .register(meterRegistry);
        this.failureTimer = Timer.builder("ai.rerank.duration")
                .tag("outcome", "failure")
                .description("AI 리랭킹 호출 소요시간")
                .register(meterRegistry);
    }

    /**
     * 후보 공고 목록을 쿼리 기준으로 재정렬한다.
     *
     * @param request 쿼리와 재정렬 대상 후보 목록
     * @return 재정렬 점수가 포함된 응답
     * @throws AiClientException ai-server 호출 실패 시
     */
    public Mono<RerankResponse> rerank(RerankRequest request) {
        Timer.Sample sample = Timer.start();
        return aiWebClient.post()
                .uri("/rerank")
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AiClientException(
                                        response.statusCode(), body)))
                )
                .bodyToMono(RerankResponse.class)
                .doOnSuccess(r -> sample.stop(successTimer))
                .doOnError(e -> sample.stop(failureTimer))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}

package com.jobai.backend.global.ai.client;

import com.jobai.backend.global.ai.dto.EmbedRequest;
import com.jobai.backend.global.ai.dto.EmbedResponse;
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
 * ai-server의 {@code POST /embed/jd}, {@code POST /embed/ncs} 엔드포인트를 호출하여
 * 텍스트를 768차원 벡터로 변환하는 클라이언트.
 *
 * <p>ai-server 서킷 브레이커가 적용되어, 서버 장애 시 호출을 즉시 차단한다.
 * 응답시간은 {@code ai.embedding.duration} 메트릭으로 수집된다.
 */
@Component
public class AiEmbeddingClient {

    private final WebClient aiWebClient;
    private final CircuitBreaker circuitBreaker;
    private final Timer successTimer;
    private final Timer failureTimer;

    public AiEmbeddingClient(@Qualifier("aiWebClient") WebClient aiWebClient,
                             @Qualifier("aiServerCircuitBreaker") CircuitBreaker circuitBreaker,
                             MeterRegistry meterRegistry) {
        this.aiWebClient = aiWebClient;
        this.circuitBreaker = circuitBreaker;
        this.successTimer = Timer.builder("ai.embedding.duration")
                .tag("outcome", "success")
                .description("AI 임베딩 호출 소요시간")
                .register(meterRegistry);
        this.failureTimer = Timer.builder("ai.embedding.duration")
                .tag("outcome", "failure")
                .description("AI 임베딩 호출 소요시간")
                .register(meterRegistry);
    }

    public Mono<EmbedResponse> embed(EmbedRequest request) {
        return embedJd(request);
    }

    public Mono<EmbedResponse> embedJd(EmbedRequest request) {
        return requestEmbedding("/embed/jd", request);
    }

    public Mono<EmbedResponse> embedNcs(EmbedRequest request) {
        return requestEmbedding("/embed/ncs", request);
    }

    private Mono<EmbedResponse> requestEmbedding(String uri, EmbedRequest request) {
        Timer.Sample sample = Timer.start();
        return aiWebClient.post()
                .uri(uri)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AiClientException(
                                        response.statusCode(),
                                        body
                                )))
                )
                .bodyToMono(EmbedResponse.class)
                .doOnSuccess(r -> sample.stop(successTimer))
                .doOnError(e -> sample.stop(failureTimer))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}

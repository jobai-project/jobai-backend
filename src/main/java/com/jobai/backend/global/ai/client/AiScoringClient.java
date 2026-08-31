package com.jobai.backend.global.ai.client;

import com.jobai.backend.global.ai.dto.ScorePrivateRequest;
import com.jobai.backend.global.ai.dto.ScorePrivateResponse;
import com.jobai.backend.global.ai.dto.ScorePublicRequest;
import com.jobai.backend.global.ai.dto.ScorePublicResponse;
import com.jobai.backend.global.ai.exception.AiClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * AI 서버의 /score/private, /score/public 엔드포인트를 호출하여
 * 이력서-공고 간 매칭 점수를 계산하는 클라이언트.
 */
@Component
public class AiScoringClient {

    private final WebClient aiWebClient;
    private final CircuitBreaker circuitBreaker;
    private final Timer privateSuccessTimer;
    private final Timer privateFailureTimer;
    private final Timer publicSuccessTimer;
    private final Timer publicFailureTimer;
    private final Counter failureCounter;

    public AiScoringClient(@Qualifier("aiWebClient") WebClient aiWebClient,
                           @Qualifier("aiServerCircuitBreaker") CircuitBreaker circuitBreaker,
                           MeterRegistry meterRegistry) {
        this.aiWebClient = aiWebClient;
        this.circuitBreaker = circuitBreaker;
        this.privateSuccessTimer = Timer.builder("ai.scoring.duration")
                .tag("type", "private").tag("outcome", "success")
                .description("AI 사기업 스코어링 호출 소요시간")
                .register(meterRegistry);
        this.privateFailureTimer = Timer.builder("ai.scoring.duration")
                .tag("type", "private").tag("outcome", "failure")
                .description("AI 사기업 스코어링 호출 소요시간")
                .register(meterRegistry);
        this.publicSuccessTimer = Timer.builder("ai.scoring.duration")
                .tag("type", "public").tag("outcome", "success")
                .description("AI 공기업 스코어링 호출 소요시간")
                .register(meterRegistry);
        this.publicFailureTimer = Timer.builder("ai.scoring.duration")
                .tag("type", "public").tag("outcome", "failure")
                .description("AI 공기업 스코어링 호출 소요시간")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("ai.scoring.failures")
                .description("AI 스코어링 호출 실패 횟수")
                .register(meterRegistry);
    }

    public Mono<ScorePrivateResponse> scorePrivate(ScorePrivateRequest request) {
        Timer.Sample sample = Timer.start();
        return aiWebClient.post()
                .uri("/score/private")
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
                .bodyToMono(ScorePrivateResponse.class)
                .doOnSuccess(r -> sample.stop(privateSuccessTimer))
                .doOnError(e -> {
                    sample.stop(privateFailureTimer);
                    failureCounter.increment();
                })
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    public Mono<ScorePublicResponse> scorePublic(ScorePublicRequest request) {
        Timer.Sample sample = Timer.start();
        return aiWebClient.post()
                .uri("/score/public")
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
                .bodyToMono(ScorePublicResponse.class)
                .doOnSuccess(r -> sample.stop(publicSuccessTimer))
                .doOnError(e -> {
                    sample.stop(publicFailureTimer);
                    failureCounter.increment();
                })
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}

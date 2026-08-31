package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * 외부 공공 API(data.go.kr)를 호출하여 채용공고 상세 정보를 가져오는 클라이언트.
 *
 * <p>public-data-api 서킷 브레이커가 적용되어, API 장애 시 호출을 즉시 차단한다.
 * 응답시간은 {@code public.api.detail.duration} 메트릭으로 수집된다.
 */
@Slf4j
@Component
public class PublicJobDetailApiClient {

    private final CircuitBreaker circuitBreaker;
    private final Timer successTimer;
    private final Timer failureTimer;

    public PublicJobDetailApiClient(
            @Qualifier("publicDataApiCircuitBreaker") CircuitBreaker circuitBreaker,
            MeterRegistry meterRegistry) {
        this.circuitBreaker = circuitBreaker;
        this.successTimer = Timer.builder("public.api.detail.duration")
                .tag("outcome", "success")
                .description("공공데이터 API 상세 조회 소요시간")
                .register(meterRegistry);
        this.failureTimer = Timer.builder("public.api.detail.duration")
                .tag("outcome", "failure")
                .description("공공데이터 API 상세 조회 소요시간")
                .register(meterRegistry);
    }

    public Mono<PublicJobDetailResponse> fetchJobDetail(WebClient webClient, Long sn, String serviceKey) {
        Timer.Sample sample = Timer.start();
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/1051000/recruitment/detail")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("sn", sn)
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(PublicJobDetailResponse.class)
                .timeout(Duration.ofSeconds(10)) // 개별 상세 조회 타임아웃
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))) // 재시도 (서킷 브레이커 안쪽)
                .doOnSuccess(r -> sample.stop(successTimer))
                .doOnError(e -> {
                    sample.stop(failureTimer);
                    log.error("상세 정보 조회 실패 [sn: {}]: {}", sn, e.getMessage());
                })
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}

package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/*
    외부 공공 API를 호출하여 상세 정보를 가져오는 클래스
 */
@Slf4j
@Component
public class PublicJobDetailApiClient {

    public Mono<PublicJobDetailResponse> fetchJobDetail(WebClient webClient, Long sn, String serviceKey) {
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
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))) // 재시도
                .doOnError(e -> log.error("상세 정보 조회 실패 [sn: {}]: {}", sn, e.getMessage()));
    }
}

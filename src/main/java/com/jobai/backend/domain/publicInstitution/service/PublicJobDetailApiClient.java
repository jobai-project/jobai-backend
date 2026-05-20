package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/*
    외부 공공 API를 호출하여 상세 정보를 가져오는 클래스
 */
@Slf4j
@Component
public class PublicJobDetailApiClient {

    public PublicJobDetailResponse fetchJobDetail(WebClient webClient, Long sn, String serviceKey) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/1051000/recruitment/detail")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("sn", sn)
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(PublicJobDetailResponse.class)
                .block();
    }
}

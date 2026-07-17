package com.jobai.backend.domain.ai.client;

import com.jobai.backend.domain.ai.dto.RerankRequest;
import com.jobai.backend.domain.ai.dto.RerankResponse;
import com.jobai.backend.domain.ai.exception.AiClientException;
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

    public AiRerankClient(@Qualifier("aiWebClient") WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

    /**
     * 후보 공고 목록을 쿼리 기준으로 재정렬한다.
     *
     * @param request 쿼리와 재정렬 대상 후보 목록
     * @return 재정렬 점수가 포함된 응답
     * @throws AiClientException ai-server 호출 실패 시
     */
    public Mono<RerankResponse> rerank(RerankRequest request) {
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
                .bodyToMono(RerankResponse.class);
    }
}

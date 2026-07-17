package com.jobai.backend.domain.ai.client;

import com.jobai.backend.domain.ai.dto.RerankRequest;
import com.jobai.backend.domain.ai.dto.RerankResponse;
import com.jobai.backend.domain.ai.exception.AiClientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AiRerankClient {

    private final WebClient aiWebClient;

    public AiRerankClient(@Qualifier("aiWebClient") WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

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

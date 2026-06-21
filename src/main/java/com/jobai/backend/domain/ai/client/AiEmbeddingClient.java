package com.jobai.backend.domain.ai.client;

import com.jobai.backend.domain.ai.dto.EmbedRequest;
import com.jobai.backend.domain.ai.dto.EmbedResponse;
import com.jobai.backend.domain.ai.exception.AiClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiEmbeddingClient {

    @Qualifier("aiWebClient")
    private final WebClient aiWebClient;

    public Mono<EmbedResponse> embed(EmbedRequest request) {
        return aiWebClient.post()
                .uri("/embed")
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AiClientException(
                                        HttpStatus.valueOf(response.statusCode().value()),
                                        body
                                )))
                )
                .bodyToMono(EmbedResponse.class);
    }
}

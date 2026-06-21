package com.jobai.backend.domain.ai.client;

import com.jobai.backend.domain.ai.dto.EmbedRequest;
import com.jobai.backend.domain.ai.dto.EmbedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
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
                .bodyToMono(EmbedResponse.class);
    }
}

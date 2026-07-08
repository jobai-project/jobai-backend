package com.jobai.backend.domain.ai.client;

import com.jobai.backend.domain.ai.dto.EmbedRequest;
import com.jobai.backend.domain.ai.dto.EmbedResponse;
import com.jobai.backend.domain.ai.exception.AiClientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AiEmbeddingClient {

    private final WebClient aiWebClient;

    // Lombok @RequiredArgsConstructor는 필드의 @Qualifier를 생성자 파라미터로 복사하지 않아
    public AiEmbeddingClient(@Qualifier("aiWebClient") WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
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
                .bodyToMono(EmbedResponse.class);
    }
}

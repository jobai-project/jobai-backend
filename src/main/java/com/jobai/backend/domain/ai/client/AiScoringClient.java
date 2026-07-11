package com.jobai.backend.domain.ai.client;

import com.jobai.backend.domain.ai.dto.ScorePrivateRequest;
import com.jobai.backend.domain.ai.dto.ScorePrivateResponse;
import com.jobai.backend.domain.ai.dto.ScorePublicRequest;
import com.jobai.backend.domain.ai.dto.ScorePublicResponse;
import com.jobai.backend.domain.ai.exception.AiClientException;
import lombok.RequiredArgsConstructor;
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

    // Lombok @RequiredArgsConstructor는 필드의 @Qualifier를 생성자 파라미터로 복사하지 않아
    public AiScoringClient(@Qualifier("aiWebClient") WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

    public Mono<ScorePrivateResponse> scorePrivate(ScorePrivateRequest request) {
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
                .bodyToMono(ScorePrivateResponse.class);
    }

    public Mono<ScorePublicResponse> scorePublic(ScorePublicRequest request) {
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
                .bodyToMono(ScorePublicResponse.class);
    }
}

package com.jobai.backend.domain.ai.client;

import com.jobai.backend.global.ai.client.AiEmbeddingClient;
import com.jobai.backend.global.ai.dto.EmbedRequest;
import com.jobai.backend.global.config.WebClientConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiEmbeddingClient가 실제 스프링 컨테이너에서 @Qualifier("aiWebClient") 빈을 정확히 주입받는지 검증한다.
 *
 * AiEmbeddingClientTest(단위 테스트)는 `new AiEmbeddingClient(webClient)`처럼 객체를 직접 생성하기 때문에
 * 배선(생성자 파라미터에 어떤 빈이 실제로 주입되는지) 문제를 잡지 못한다. 이 테스트는 WebClientConfig가 만드는
 * 두 WebClient 빈(webClient=@Primary, aiWebClient) 중 반드시 aiWebClient가 주입되는지를 실제 Spring
 * ApplicationContext로 검증한다.
 */
@SpringBootTest(classes = {WebClientConfig.class, AiEmbeddingClient.class})
class AiEmbeddingClientWiringTest {

    static MockWebServer aiServer;

    @DynamicPropertySource
    static void registerAiServerUrl(DynamicPropertyRegistry registry) throws IOException {
        aiServer = new MockWebServer();
        aiServer.start();
        registry.add("ai-server.base-url", () -> aiServer.url("/").toString().replaceAll("/$", ""));
    }

    @AfterAll
    static void tearDown() throws IOException {
        aiServer.shutdown();
    }

    @Autowired
    private AiEmbeddingClient aiEmbeddingClient;

    @Test
    @DisplayName("실제 스프링 컨텍스트에서 ai-server.base-url(aiWebClient)로 요청이 나간다")
    void embedJd_실제로_ai서버로_요청간다() throws InterruptedException {
        aiServer.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody("{\"vector\":[0.1]}"));

        aiEmbeddingClient.embedJd(new EmbedRequest("hello")).block();

        RecordedRequest recorded = aiServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getPath()).isEqualTo("/embed/jd");
    }
}

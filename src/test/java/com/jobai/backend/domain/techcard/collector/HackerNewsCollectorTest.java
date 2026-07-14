package com.jobai.backend.domain.techcard.collector;

import com.jobai.backend.domain.techcard.entity.ContentSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HackerNewsCollectorTest {

    private MockWebServer server;
    private HackerNewsCollector collector;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        collector = new HackerNewsCollector();

        // WebClient의 baseUrl을 MockWebServer로 교체
        Field webClientField = HackerNewsCollector.class.getDeclaredField("webClient");
        webClientField.setAccessible(true);
        webClientField.set(collector, org.springframework.web.reactive.function.client.WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("source()는 HACKERNEWS를 반환한다")
    void sourceIsHackerNews() {
        assertThat(collector.source()).isEqualTo(ContentSource.HACKERNEWS);
    }

    @Test
    @DisplayName("score 상위 10개 기사만 반환한다")
    void returnsTop10ByScore() {
        // topstories 응답: 12개 ID
        StringBuilder ids = new StringBuilder("[");
        for (int i = 1; i <= 12; i++) {
            if (i > 1) ids.append(",");
            ids.append(i);
        }
        ids.append("]");
        server.enqueue(new MockResponse()
                .setBody(ids.toString())
                .addHeader("Content-Type", "application/json"));

        // 각 스토리 응답 (score 다양)
        for (int i = 1; i <= 12; i++) {
            enqueueStory(i, "Story " + i, i * 100);
        }

        List<RawArticle> result = collector.collect();

        assertThat(result).hasSize(10);
        // score 최고(1200)가 첫 번째
        assertThat(result.get(0).title()).isEqualTo("Story 12");
    }

    @Test
    @DisplayName("topstories API 실패 시 빈 리스트 반환")
    void emptyOnTopStoriesFailure() {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<RawArticle> result = collector.collect();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("externalId 형식은 hn:{storyId}이다")
    void externalIdFormat() {
        server.enqueue(new MockResponse()
                .setBody("[42]")
                .addHeader("Content-Type", "application/json"));
        enqueueStory(42, "Test Story", 100);

        List<RawArticle> result = collector.collect();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).externalId()).isEqualTo("hn:42");
    }

    @Test
    @DisplayName("url이 없으면 HackerNews 댓글 페이지 URL을 사용한다")
    void fallbackUrlWhenMissing() {
        server.enqueue(new MockResponse()
                .setBody("[99]")
                .addHeader("Content-Type", "application/json"));

        // url 필드 없는 스토리 (Ask HN 등)
        server.enqueue(new MockResponse()
                .setBody("{\"id\":99,\"title\":\"Ask HN: Something\",\"score\":50,\"time\":1720000000}")
                .addHeader("Content-Type", "application/json"));

        List<RawArticle> result = collector.collect();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).url()).contains("news.ycombinator.com/item?id=99");
    }

    private void enqueueStory(int id, String title, int score) {
        server.enqueue(new MockResponse()
                .setBody("{\"id\":%d,\"title\":\"%s\",\"url\":\"https://example.com/%d\",\"score\":%d,\"time\":1720000000}"
                        .formatted(id, title, id, score))
                .addHeader("Content-Type", "application/json"));
    }
}

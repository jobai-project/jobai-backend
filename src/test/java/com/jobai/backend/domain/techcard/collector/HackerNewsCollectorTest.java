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
    @DisplayName("score 상위 5개 기사만 반환한다")
    void returnsTop5ByScore() {
        // topstories 응답: 6개 ID
        server.enqueue(new MockResponse()
                .setBody("[1,2,3,4,5,6]")
                .addHeader("Content-Type", "application/json"));

        // 각 스토리 응답 (score 다양)
        enqueueStory(1, "Low Score Story", 10);
        enqueueStory(2, "High Score Story", 500);
        enqueueStory(3, "Medium Story", 100);
        enqueueStory(4, "Very High Score", 800);
        enqueueStory(5, "Another Medium", 150);
        enqueueStory(6, "Decent Story", 200);

        List<RawArticle> result = collector.collect();

        assertThat(result).hasSize(5);
        // score 순: 800, 500, 200, 150, 100
        assertThat(result.get(0).title()).isEqualTo("Very High Score");
        assertThat(result.get(1).title()).isEqualTo("High Score Story");
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

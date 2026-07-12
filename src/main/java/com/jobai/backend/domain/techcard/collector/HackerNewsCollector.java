package com.jobai.backend.domain.techcard.collector;

import com.jobai.backend.domain.techcard.entity.ContentSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HackerNewsCollector implements ArticleCollector {

    private static final String BASE_URL = "https://hacker-news.firebaseio.com/v0";
    private static final int FETCH_COUNT = 20;
    private static final int TOP_COUNT = 5;
    private static final int CONCURRENCY = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public HackerNewsCollector() {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    @Override
    public ContentSource source() {
        return ContentSource.HACKERNEWS;
    }

    @Override
    public List<RawArticle> collect() {
        try {
            List<Integer> storyIds = fetchTopStoryIds();
            List<Integer> targetIds = storyIds.subList(0, Math.min(storyIds.size(), FETCH_COUNT));

            List<StoryWithScore> stories = Flux.fromIterable(targetIds)
                    .flatMap(this::fetchStoryWithScore, CONCURRENCY)
                    .collectList()
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (stories == null || stories.isEmpty()) {
                return List.of();
            }

            // score 상위 5개만 반환
            return stories.stream()
                    .sorted(Comparator.comparingInt(StoryWithScore::score).reversed())
                    .limit(TOP_COUNT)
                    .map(StoryWithScore::article)
                    .toList();
        } catch (Exception e) {
            log.error("[HackerNews] 수집 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> fetchTopStoryIds() {
        List<Integer> ids = webClient.get()
                .uri("/topstories.json")
                .retrieve()
                .bodyToMono(List.class)
                .timeout(TIMEOUT)
                .block();
        return ids != null ? ids : List.of();
    }

    private record StoryWithScore(RawArticle article, int score) {}

    @SuppressWarnings("unchecked")
    private Mono<StoryWithScore> fetchStoryWithScore(Integer storyId) {
        return webClient.get()
                .uri("/item/{id}.json", storyId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
                .map(item -> {
                    String title = (String) item.getOrDefault("title", "");
                    String url = (String) item.getOrDefault("url", "https://news.ycombinator.com/item?id=" + storyId);
                    Number score = (Number) item.getOrDefault("score", 0);
                    Number time = (Number) item.get("time");
                    LocalDateTime publishedAt = time != null
                            ? Instant.ofEpochSecond(time.longValue()).atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime()
                            : null;

                    RawArticle article = new RawArticle(
                            ContentSource.HACKERNEWS,
                            "hn:" + storyId,
                            title,
                            url,
                            "",
                            publishedAt
                    );
                    return new StoryWithScore(article, score.intValue());
                })
                .onErrorResume(e -> {
                    log.warn("[HackerNews] 스토리 {} 조회 실패: {}", storyId, e.getMessage());
                    return Mono.empty();
                });
    }
}

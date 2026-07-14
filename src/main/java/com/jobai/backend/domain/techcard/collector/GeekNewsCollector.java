package com.jobai.backend.domain.techcard.collector;

import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
public class GeekNewsCollector implements ArticleCollector {

    private static final String RSS_URL = "https://news.hada.io/rss/news";
    private static final int FETCH_COUNT = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public GeekNewsCollector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public ContentSource source() {
        return ContentSource.GEEKNEWS;
    }

    @Override
    public List<RawArticle> collect() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RSS_URL))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            SyndFeed feed = new SyndFeedInput().build(new XmlReader(response.body()));
            List<SyndEntry> entries = feed.getEntries();

            return entries.stream()
                    .limit(FETCH_COUNT)
                    .map(this::toRawArticle)
                    .toList();
        } catch (Exception e) {
            log.error("[GeekNews] 수집 실패: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private RawArticle toRawArticle(SyndEntry entry) {
        String url = entry.getLink() != null ? entry.getLink() : "";
        String title = entry.getTitle() != null ? entry.getTitle() : "";
        LocalDateTime publishedAt = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant().atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime()
                : null;

        return new RawArticle(
                ContentSource.GEEKNEWS,
                "gn:" + sha256(url),
                title,
                url,
                "",
                publishedAt
        );
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}

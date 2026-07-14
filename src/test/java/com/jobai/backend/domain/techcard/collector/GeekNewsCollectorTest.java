package com.jobai.backend.domain.techcard.collector;

import com.jobai.backend.domain.techcard.entity.ContentSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeekNewsCollectorTest {

    private final GeekNewsCollector collector = new GeekNewsCollector();

    @Test
    @DisplayName("source()는 GEEKNEWS를 반환한다")
    void sourceIsGeekNews() {
        assertThat(collector.source()).isEqualTo(ContentSource.GEEKNEWS);
    }

    @Test
    @DisplayName("externalId는 gn: 접두사 + SHA-256 해시 16자리 형식이다")
    void externalIdFormat() {
        String hash = collector.sha256("https://example.com/article");
        String externalId = "gn:" + hash;

        assertThat(externalId).startsWith("gn:");
        assertThat(hash).hasSize(16);
        assertThat(hash).matches("[0-9a-f]{16}");
    }

    @Test
    @DisplayName("동일 URL은 동일 해시를 반환한다")
    void sameUrlSameHash() {
        String hash1 = collector.sha256("https://example.com/article");
        String hash2 = collector.sha256("https://example.com/article");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("다른 URL은 다른 해시를 반환한다")
    void differentUrlDifferentHash() {
        String hash1 = collector.sha256("https://example.com/article1");
        String hash2 = collector.sha256("https://example.com/article2");

        assertThat(hash1).isNotEqualTo(hash2);
    }
}

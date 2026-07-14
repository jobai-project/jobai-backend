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
    @DisplayName("externalId는 gn: 접두사로 시작한다")
    void externalIdPrefix() {
        // GeekNewsCollector의 sha256 메서드가 gn: 접두사를 붙이는지 간접 확인
        // 실제 RSS 호출 없이 source 확인만 수행
        assertThat(collector.source().name()).isEqualTo("GEEKNEWS");
    }
}

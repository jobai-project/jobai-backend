package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.ScrapRankingResponse;
import com.jobai.backend.domain.scrap.repository.MemberScrapHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ScrapRankingServiceTest {

    private MemberScrapHistoryRepository scrapHistoryRepository;
    private ScrapRankingService service;

    @BeforeEach
    void setUp() {
        scrapHistoryRepository = Mockito.mock(MemberScrapHistoryRepository.class);
        service = new ScrapRankingService(scrapHistoryRepository);
    }

    @Test
    @DisplayName("스크랩 순위는 전체 사용자 스크랩 수 기준으로 최대 5개를 반환한다")
    void getPopularScrapsReturnsTopFiveByScrapCount() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 10, 0);
        when(scrapHistoryRepository.findPopularPublicScraps(any()))
                .thenReturn(List.of(
                        row("PUBLIC", 1L, "공기업 백엔드", "한국전력공사", 10L, now.minusHours(1)),
                        row("PUBLIC", 2L, "공기업 인턴", "한국수자원공사", 5L, now.minusHours(2)),
                        row("PUBLIC", 3L, "공기업 데이터", "한국도로공사", 4L, now.minusHours(3))
                ));
        when(scrapHistoryRepository.findPopularPrivateScraps(any()))
                .thenReturn(List.of(
                        row("PRIVATE", 11L, "Java 백엔드 개발자", "카카오페이", 12L, now),
                        row("PRIVATE", 12L, "Spring 백엔드 엔지니어", "토스", 7L, now.minusMinutes(30)),
                        row("PRIVATE", 13L, "서버 개발자", "라인플러스", 6L, now.minusMinutes(40))
                ));

        ScrapRankingResponse response = service.getPopularScraps();

        assertThat(response.rankings()).hasSize(5);
        assertThat(response.rankings()).extracting(ScrapRankingResponse.ScrapRankingItem::rank)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(response.rankings()).extracting(ScrapRankingResponse.ScrapRankingItem::sourceId)
                .containsExactly(11L, 1L, 12L, 13L, 2L);
    }

    private Object[] row(String source, Long sourceId, String title, String companyName,
                         Long scrapCount, LocalDateTime lastScrappedAt) {
        return new Object[]{source, sourceId, title, companyName, scrapCount, lastScrappedAt};
    }
}

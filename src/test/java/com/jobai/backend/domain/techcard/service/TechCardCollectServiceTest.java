package com.jobai.backend.domain.techcard.service;

import com.jobai.backend.domain.bloom.service.TechCardBloomFilterService;
import com.jobai.backend.domain.techcard.collector.ArticleCollector;
import com.jobai.backend.domain.techcard.collector.RawArticle;
import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.entity.TechCard;
import com.jobai.backend.domain.techcard.repository.TechCardRepository;
import com.jobai.backend.domain.techcard.service.TechCardSummarizeService.PickedSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TechCardCollectServiceTest {

    private ArticleCollector collector;
    private TechCardBloomFilterService bloomFilter;
    private TechCardSummarizeService summarizeService;
    private TechCardRepository techCardRepository;
    private TechCardCollectService collectService;

    @BeforeEach
    void setUp() {
        collector = Mockito.mock(ArticleCollector.class);
        bloomFilter = Mockito.mock(TechCardBloomFilterService.class);
        summarizeService = Mockito.mock(TechCardSummarizeService.class);
        techCardRepository = Mockito.mock(TechCardRepository.class);

        when(collector.source()).thenReturn(ContentSource.HACKERNEWS);

        collectService = new TechCardCollectService(
                List.of(collector),
                Optional.of(bloomFilter),
                summarizeService,
                techCardRepository
        );
    }

    @Test
    @DisplayName("정상 흐름: 수집 → Bloom 필터링 → LLM 선별 → 선택된 1건 저장")
    void collectsAndSavesPicked() {
        List<RawArticle> articles = List.of(
                article("hn:1", "AI Tool Released"),
                article("hn:2", "Go Popularity Surges"),
                article("hn:3", "New CSS Feature")
        );
        when(collector.collect()).thenReturn(articles);
        when(bloomFilter.mightContain(anyString())).thenReturn(false);
        when(summarizeService.pickAndSummarize(anyList()))
                .thenReturn(new PickedSummary(1, "Go 인기가 심상치 않아요", "백엔드 공고에서 Go 언급이 늘고 있어요"));

        collectService.collectAndSummarize();

        ArgumentCaptor<TechCard> captor = ArgumentCaptor.forClass(TechCard.class);
        verify(techCardRepository).save(captor.capture());

        TechCard saved = captor.getValue();
        assertThat(saved.getExternalId()).isEqualTo("hn:2");
        assertThat(saved.getHeadline()).isEqualTo("Go 인기가 심상치 않아요");
        assertThat(saved.getSubtext()).isEqualTo("백엔드 공고에서 Go 언급이 늘고 있어요");
    }

    @Test
    @DisplayName("Bloom 필터에 이미 있는 기사는 제외된다")
    void bloomFilterExcludesDuplicates() {
        List<RawArticle> articles = List.of(
                article("hn:1", "Old News"),
                article("hn:2", "New News")
        );
        when(collector.collect()).thenReturn(articles);
        when(bloomFilter.mightContain("hn:1")).thenReturn(true);
        when(bloomFilter.mightContain("hn:2")).thenReturn(false);
        when(summarizeService.pickAndSummarize(anyList()))
                .thenReturn(new PickedSummary(0, "제목", "부연"));

        collectService.collectAndSummarize();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RawArticle>> captor = ArgumentCaptor.forClass(List.class);
        verify(summarizeService).pickAndSummarize(captor.capture());

        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).externalId()).isEqualTo("hn:2");
    }

    @Test
    @DisplayName("모든 기사가 Bloom 필터에 있으면 LLM 호출하지 않는다")
    void allFilteredByBloom() {
        when(collector.collect()).thenReturn(List.of(article("hn:1", "Title")));
        when(bloomFilter.mightContain("hn:1")).thenReturn(true);

        collectService.collectAndSummarize();

        verifyNoInteractions(summarizeService);
        verify(techCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("수집 결과가 비어있으면 LLM 호출하지 않는다")
    void emptyCollectionSkipsLlm() {
        when(collector.collect()).thenReturn(List.of());

        collectService.collectAndSummarize();

        verifyNoInteractions(summarizeService);
        verify(techCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("LLM 선별 실패(null 반환)시 저장하지 않는다")
    void llmFailureSkipsSave() {
        when(collector.collect()).thenReturn(List.of(article("hn:1", "Title")));
        when(bloomFilter.mightContain(anyString())).thenReturn(false);
        when(summarizeService.pickAndSummarize(anyList())).thenReturn(null);

        collectService.collectAndSummarize();

        verify(techCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장 후 모든 신규 기사를 Bloom 필터에 등록한다")
    void registersAllToBloomAfterSave() {
        List<RawArticle> articles = List.of(
                article("hn:1", "Title1"),
                article("hn:2", "Title2"),
                article("hn:3", "Title3")
        );
        when(collector.collect()).thenReturn(articles);
        when(bloomFilter.mightContain(anyString())).thenReturn(false);
        when(summarizeService.pickAndSummarize(anyList()))
                .thenReturn(new PickedSummary(0, "제목", "부연"));

        collectService.collectAndSummarize();

        verify(bloomFilter).add("hn:1");
        verify(bloomFilter).add("hn:2");
        verify(bloomFilter).add("hn:3");
    }

    @Test
    @DisplayName("Bloom 필터 없이(local 프로파일) 정상 동작한다")
    void worksWithoutBloomFilter() {
        collectService = new TechCardCollectService(
                List.of(collector),
                Optional.empty(),
                summarizeService,
                techCardRepository
        );

        when(collector.collect()).thenReturn(List.of(article("hn:1", "Title")));
        when(summarizeService.pickAndSummarize(anyList()))
                .thenReturn(new PickedSummary(0, "제목", "부연"));

        collectService.collectAndSummarize();

        verify(techCardRepository).save(any());
    }

    @Test
    @DisplayName("수집기 예외 발생 시 전체가 중단되지 않는다")
    void collectorExceptionDoesNotStopAll() {
        ArticleCollector failCollector = Mockito.mock(ArticleCollector.class);
        when(failCollector.source()).thenReturn(ContentSource.HACKERNEWS);
        when(failCollector.collect()).thenThrow(new RuntimeException("네트워크 오류"));

        ArticleCollector okCollector = Mockito.mock(ArticleCollector.class);
        when(okCollector.source()).thenReturn(ContentSource.HACKERNEWS);
        when(okCollector.collect()).thenReturn(List.of(article("hn:1", "Title")));

        collectService = new TechCardCollectService(
                List.of(failCollector, okCollector),
                Optional.empty(),
                summarizeService,
                techCardRepository
        );

        when(summarizeService.pickAndSummarize(anyList()))
                .thenReturn(new PickedSummary(0, "제목", "부연"));

        collectService.collectAndSummarize();

        verify(techCardRepository).save(any());
    }

    private RawArticle article(String externalId, String title) {
        return new RawArticle(ContentSource.HACKERNEWS, externalId, title,
                "https://example.com", "", null);
    }
}

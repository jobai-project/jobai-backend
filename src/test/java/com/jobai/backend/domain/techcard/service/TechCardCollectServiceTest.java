package com.jobai.backend.domain.techcard.service;

import com.jobai.backend.domain.bloom.service.TechCardBloomFilterService;
import com.jobai.backend.domain.techcard.collector.ArticleCollector;
import com.jobai.backend.domain.techcard.collector.RawArticle;
import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.entity.TechCard;
import com.jobai.backend.domain.techcard.repository.TechCardRepository;
import com.jobai.backend.domain.techcard.service.TechCardSummarizeService.CardSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
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
    @DisplayName("정상 흐름: 수집 → Bloom 필터링 → 배치 요약 → 전체 저장")
    void collectsAndSavesAll() {
        List<RawArticle> articles = List.of(
                article("hn:1", "AI Tool Released"),
                article("hn:2", "Go Popularity Surges"),
                article("hn:3", "New CSS Feature")
        );
        when(collector.collect()).thenReturn(articles);
        when(bloomFilter.mightContain(anyString())).thenReturn(false);
        when(summarizeService.summarize(anyList())).thenReturn(List.of(
                new CardSummary("AI 도구 출시됐어요", "개발자 생산성이 올라갈 것 같아요"),
                new CardSummary("Go 인기가 심상치 않아요", "백엔드 공고에서 Go 언급이 늘고 있어요"),
                new CardSummary("CSS 새 기능이 나왔어요", "프론트엔드 개발이 편해질 것 같아요")
        ));

        collectService.collectAndSummarize();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TechCard>> captor = ArgumentCaptor.forClass(List.class);
        verify(techCardRepository).saveAll(captor.capture());

        List<TechCard> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getExternalId()).isEqualTo("hn:1");
        assertThat(saved.get(1).getHeadline()).isEqualTo("Go 인기가 심상치 않아요");
    }

    @Test
    @DisplayName("요약 실패한 항목은 건너뛰고 나머지만 저장한다")
    void skipsNullSummaries() {
        List<RawArticle> articles = List.of(
                article("hn:1", "Title 1"),
                article("hn:2", "Title 2"),
                article("hn:3", "Title 3")
        );
        when(collector.collect()).thenReturn(articles);
        when(bloomFilter.mightContain(anyString())).thenReturn(false);
        when(summarizeService.summarize(anyList())).thenReturn(Arrays.asList(
                new CardSummary("제목1", "부연1"),
                null,
                new CardSummary("제목3", "부연3")
        ));

        collectService.collectAndSummarize();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TechCard>> captor = ArgumentCaptor.forClass(List.class);
        verify(techCardRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(2);
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
        when(summarizeService.summarize(anyList())).thenReturn(List.of(
                new CardSummary("제목", "부연")
        ));

        collectService.collectAndSummarize();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RawArticle>> captor = ArgumentCaptor.forClass(List.class);
        verify(summarizeService).summarize(captor.capture());

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
        verify(techCardRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("수집 결과가 비어있으면 LLM 호출하지 않는다")
    void emptyCollectionSkipsLlm() {
        when(collector.collect()).thenReturn(List.of());

        collectService.collectAndSummarize();

        verifyNoInteractions(summarizeService);
        verify(techCardRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("저장 후 모든 신규 기사를 Bloom 필터에 등록한다")
    void registersAllToBloomAfterSave() {
        List<RawArticle> articles = List.of(
                article("hn:1", "Title1"),
                article("hn:2", "Title2")
        );
        when(collector.collect()).thenReturn(articles);
        when(bloomFilter.mightContain(anyString())).thenReturn(false);
        when(summarizeService.summarize(anyList())).thenReturn(List.of(
                new CardSummary("제목1", "부연1"),
                new CardSummary("제목2", "부연2")
        ));

        collectService.collectAndSummarize();

        verify(bloomFilter).add("hn:1");
        verify(bloomFilter).add("hn:2");
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
        when(summarizeService.summarize(anyList())).thenReturn(List.of(
                new CardSummary("제목", "부연")
        ));

        collectService.collectAndSummarize();

        verify(techCardRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("수집기 예외 발생 시 전체가 중단되지 않는다")
    void collectorExceptionDoesNotStopAll() {
        ArticleCollector failCollector = Mockito.mock(ArticleCollector.class);
        when(failCollector.source()).thenReturn(ContentSource.HACKERNEWS);
        when(failCollector.collect()).thenThrow(new RuntimeException("네트워크 오류"));

        ArticleCollector okCollector = Mockito.mock(ArticleCollector.class);
        when(okCollector.source()).thenReturn(ContentSource.GEEKNEWS);
        when(okCollector.collect()).thenReturn(List.of(article("gn:1", "Title")));

        collectService = new TechCardCollectService(
                List.of(failCollector, okCollector),
                Optional.empty(),
                summarizeService,
                techCardRepository
        );

        when(summarizeService.summarize(anyList())).thenReturn(List.of(
                new CardSummary("제목", "부연")
        ));

        collectService.collectAndSummarize();

        verify(techCardRepository).saveAll(anyList());
    }

    private RawArticle article(String externalId, String title) {
        return new RawArticle(ContentSource.HACKERNEWS, externalId, title,
                "https://example.com", "", null);
    }
}

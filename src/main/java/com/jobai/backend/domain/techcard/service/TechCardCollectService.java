package com.jobai.backend.domain.techcard.service;

import com.jobai.backend.domain.bloom.service.TechCardBloomFilterService;
import com.jobai.backend.domain.techcard.collector.ArticleCollector;
import com.jobai.backend.domain.techcard.collector.RawArticle;
import com.jobai.backend.domain.techcard.entity.TechCard;
import com.jobai.backend.domain.techcard.repository.TechCardRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 테크 카드 수집 오케스트레이션 서비스.
 * <p>등록된 모든 {@link ArticleCollector}로부터 기사를 수집하고,
 * Bloom Filter로 중복을 제거한 뒤, LLM 요약을 거쳐 DB에 저장한다.</p>
 * <p>소스별로 try-catch가 격리되어 있어 하나의 소스 실패가 다른 소스에 영향을 주지 않는다.</p>
 */
@Slf4j
@Service
public class TechCardCollectService {

    private final List<ArticleCollector> collectors;
    private final Optional<TechCardBloomFilterService> bloomFilter;
    private final TechCardSummarizeService summarizeService;
    private final TechCardRepository techCardRepository;

    public TechCardCollectService(List<ArticleCollector> collectors,
                                  Optional<TechCardBloomFilterService> bloomFilter,
                                  TechCardSummarizeService summarizeService,
                                  TechCardRepository techCardRepository) {
        this.collectors = collectors;
        this.bloomFilter = bloomFilter;
        this.summarizeService = summarizeService;
        this.techCardRepository = techCardRepository;
    }

    /** 모든 소스에서 기사를 수집하고 요약하여 저장한다. 스케줄러에서 호출된다. */
    public void collectAndSummarize() {
        for (ArticleCollector collector : collectors) {
            try {
                collectFromSource(collector);
            } catch (Exception e) {
                log.error("[TechCard] {} 수집 실패: {}", collector.source(), e.getMessage(), e);
            }
        }
    }

    private void collectFromSource(ArticleCollector collector) {
        List<RawArticle> articles = collector.collect();
        log.info("[TechCard] {} — {}건 수집", collector.source(), articles.size());

        List<RawArticle> newArticles = articles.stream()
                .filter(a -> bloomFilter.map(bf -> !bf.mightContain(a.externalId())).orElse(true))
                .toList();
        log.info("[TechCard] {} — {}건 신규 (Bloom 통과)", collector.source(), newArticles.size());

        if (newArticles.isEmpty()) {
            return;
        }

        List<TechCardSummarizeService.CardSummary> summaries = summarizeService.summarize(newArticles);

        List<TechCard> cards = new ArrayList<>();
        for (int i = 0; i < newArticles.size(); i++) {
            RawArticle raw = newArticles.get(i);
            TechCardSummarizeService.CardSummary summary = summaries.get(i);
            if (summary == null) {
                log.warn("[TechCard] {} — '{}' 요약 실패, 건너뜀", collector.source(), raw.title());
                continue;
            }

            cards.add(TechCard.builder()
                    .source(raw.source())
                    .externalId(raw.externalId())
                    .originalTitle(raw.title())
                    .originalUrl(raw.url())
                    .headline(summary.headline())
                    .subtext(summary.subtext())
                    .publishedAt(raw.publishedAt())
                    .build());
        }

        techCardRepository.saveAll(cards);
        bloomFilter.ifPresent(bf -> cards.forEach(c -> bf.add(c.getExternalId())));
        log.info("[TechCard] {} — {}건 저장 완료", collector.source(), cards.size());
    }
}

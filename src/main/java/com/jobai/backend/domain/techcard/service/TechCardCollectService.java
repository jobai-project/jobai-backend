package com.jobai.backend.domain.techcard.service;

import com.jobai.backend.domain.bloom.service.TechCardBloomFilterService;
import com.jobai.backend.domain.techcard.collector.ArticleCollector;
import com.jobai.backend.domain.techcard.collector.RawArticle;
import com.jobai.backend.domain.techcard.entity.TechCard;
import com.jobai.backend.domain.techcard.repository.TechCardRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    @Transactional
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

        TechCardSummarizeService.PickedSummary picked = summarizeService.pickAndSummarize(newArticles);
        if (picked == null) {
            log.warn("[TechCard] {} — LLM 선별 실패, 저장 건너뜀", collector.source());
            return;
        }

        RawArticle raw = newArticles.get(picked.pickedIndex());
        TechCard card = TechCard.builder()
                .source(raw.source())
                .externalId(raw.externalId())
                .originalTitle(raw.title())
                .originalUrl(raw.url())
                .headline(picked.headline())
                .subtext(picked.subtext())
                .publishedAt(raw.publishedAt())
                .build();

        techCardRepository.save(card);
        bloomFilter.ifPresent(bf -> newArticles.forEach(a -> bf.add(a.externalId())));
        log.info("[TechCard] {} — 선별 기사 '{}' 저장 완료", collector.source(), raw.title());
    }
}

package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.ScrapRankingResponse;
import com.jobai.backend.domain.scrap.repository.MemberScrapHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScrapRankingService {

    private static final int RANKING_LIMIT = 5;

    private final MemberScrapHistoryRepository scrapHistoryRepository;

    public ScrapRankingResponse getPopularScraps() {
        PageRequest limit = PageRequest.of(0, RANKING_LIMIT);
        List<RankingCandidate> candidates = Stream.concat(
                        scrapHistoryRepository.findPopularPublicScraps(limit).stream(),
                        scrapHistoryRepository.findPopularPrivateScraps(limit).stream()
                )
                .map(this::toCandidate)
                .sorted(Comparator
                        .comparingLong(RankingCandidate::scrapCount).reversed()
                        .thenComparing(RankingCandidate::lastScrappedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RankingCandidate::source)
                        .thenComparing(RankingCandidate::sourceId, Comparator.reverseOrder()))
                .limit(RANKING_LIMIT)
                .toList();

        List<ScrapRankingResponse.ScrapRankingItem> rankings = java.util.stream.IntStream.range(0, candidates.size())
                .mapToObj(i -> {
                    RankingCandidate candidate = candidates.get(i);
                    return new ScrapRankingResponse.ScrapRankingItem(
                            i + 1,
                            candidate.source(),
                            candidate.sourceId(),
                            candidate.title(),
                            candidate.companyName(),
                            candidate.scrapCount()
                    );
                })
                .toList();

        return new ScrapRankingResponse(rankings);
    }

    private RankingCandidate toCandidate(Object[] row) {
        return new RankingCandidate(
                (String) row[0],
                (Long) row[1],
                (String) row[2],
                (String) row[3],
                ((Number) row[4]).longValue(),
                (LocalDateTime) row[5]
        );
    }

    private record RankingCandidate(
            String source,
            Long sourceId,
            String title,
            String companyName,
            long scrapCount,
            LocalDateTime lastScrappedAt
    ) {
    }
}

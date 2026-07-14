package com.jobai.backend.domain.techcard.service;

import com.jobai.backend.domain.techcard.dto.TechCardResponse;
import com.jobai.backend.domain.techcard.dto.TechCardResponse.CardItem;
import com.jobai.backend.domain.techcard.dto.TechCardResponse.RelatedJob;
import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.entity.TechCard;
import com.jobai.backend.domain.techcard.repository.TechCardRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TechCardService {

    private final TechCardRepository techCardRepository;
    private final EntityManager entityManager;

    private static final int NEWS_CARD_COUNT = 2;

    public TechCardResponse getTechCards() {
        List<CardItem> cards = new ArrayList<>();

        // 1. 신규 공고 카드
        CardItem newJobsCard = generateNewJobsCard();
        if (newJobsCard != null) {
            cards.add(newJobsCard);
        }

        // 2, 3. 테크 뉴스 카드 (오늘 수집분에서 랜덤 2건)
        List<TechCard> newsCards = techCardRepository.findTodayNewsCardsRandom();
        int newsCount = Math.min(newsCards.size(), NEWS_CARD_COUNT);
        for (int i = 0; i < newsCount; i++) {
            TechCard card = newsCards.get(i);
            cards.add(new CardItem(
                    card.getId(),
                    card.getSource().name(),
                    "테크 뉴스",
                    card.getHeadline(),
                    card.getSubtext(),
                    card.getOriginalUrl(),
                    card.getPublishedAt(),
                    card.getCreatedAt(),
                    null
            ));
        }

        return new TechCardResponse(cards);
    }

    private CardItem generateNewJobsCard() {
        @SuppressWarnings("unchecked")
        List<Tuple> jobResults = entityManager.createNativeQuery("""
                SELECT id, company, title
                FROM private_job_postings
                WHERE created_at >= CURRENT_DATE
                  AND created_at < CURRENT_DATE + 1
                  AND (job_category IS NULL OR job_category NOT IN ('비대상', '미분류'))
                ORDER BY created_at DESC
                """, Tuple.class)
                .getResultList();

        if (jobResults.isEmpty()) {
            return null;
        }

        List<RelatedJob> relatedJobs = jobResults.stream()
                .map(row -> new RelatedJob(
                        ((Number) row.get("id")).longValue(),
                        "PRIVATE",
                        (String) row.get("company"),
                        (String) row.get("title")
                ))
                .toList();

        return new CardItem(
                null,
                ContentSource.INTERNAL.name(),
                "신규 공고",
                "오늘 새로 올라온 공고가 %d건 있어요".formatted(relatedJobs.size()),
                "새로운 채용 기회를 확인해보세요",
                null,
                null,
                LocalDateTime.now(),
                relatedJobs
        );
    }
}

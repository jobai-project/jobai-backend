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

/**
 * 홈 화면 테크 카드 조회 서비스.
 * <p>신규 공고 1장 + 오늘 수집된 테크 뉴스 중 랜덤 2장을 조합하여 반환한다.
 * 데이터가 없는 카드는 생략되므로 최대 3장, 최소 0장이 반환될 수 있다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TechCardService {

    private final TechCardRepository techCardRepository;
    private final EntityManager entityManager;

    /** 홈 화면에 표시할 테크 카드 목록을 조회한다. */
    public TechCardResponse getTechCards() {
        List<CardItem> cards = new ArrayList<>();

        // 1. 신규 공고 카드
        CardItem newJobsCard = generateNewJobsCard();
        if (newJobsCard != null) {
            cards.add(newJobsCard);
        }

        // 2, 3. 테크 뉴스 카드 (오늘 수집분에서 랜덤 2건)
        List<TechCard> newsCards = techCardRepository.findTodayNewsCardsRandom();
        for (TechCard card : newsCards) {
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

    /**
     * 오늘 수집된 신규 공고를 집계하여 카드를 생성한다.
     * <p>민간 공고와 공공 공고를 모두 포함하며, 비대상/미분류 카테고리를 제외한다.</p>
     *
     * @return 신규 공고 카드 (오늘 공고가 없으면 {@code null})
     */
    private CardItem generateNewJobsCard() {
        @SuppressWarnings("unchecked")
        List<Tuple> jobResults = entityManager.createNativeQuery("""
                SELECT id, source, company_name, title FROM (
                    SELECT id, 'PRIVATE' AS source, company AS company_name, title, created_at
                    FROM private_job_postings
                    WHERE created_at >= CURRENT_DATE
                      AND created_at < CURRENT_DATE + 1
                      AND (job_category IS NULL OR job_category NOT IN ('비대상', '미분류'))
                    UNION ALL
                    SELECT jp.id, 'PUBLIC' AS source, jp.company_name, jp.title, jp.created_at
                    FROM public_job_postings pjp
                    JOIN job_postings jp ON pjp.id = jp.id
                    WHERE jp.created_at >= CURRENT_DATE
                      AND jp.created_at < CURRENT_DATE + 1
                ) AS all_jobs
                ORDER BY created_at DESC
                """, Tuple.class)
                .getResultList();

        if (jobResults.isEmpty()) {
            return null;
        }

        List<RelatedJob> relatedJobs = jobResults.stream()
                .map(row -> new RelatedJob(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("source"),
                        (String) row.get("company_name"),
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

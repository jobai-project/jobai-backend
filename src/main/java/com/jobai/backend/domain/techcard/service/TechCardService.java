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
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TechCardService {

    private final TechCardRepository techCardRepository;
    private final EntityManager entityManager;

    private static final Map<String, String> COMPANY_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("kakao", "카카오"),
            Map.entry("coupang", "쿠팡"),
            Map.entry("naver", "네이버"),
            Map.entry("toss", "토스"),
            Map.entry("woowahan", "우아한형제들"),
            Map.entry("daangn", "당근"),
            Map.entry("kurly", "컬리"),
            Map.entry("kakaopay", "카카오페이"),
            Map.entry("doodlin", "두들린"),
            Map.entry("gccompany", "지씨컴퍼니"),
            Map.entry("zigbang", "직방"),
            Map.entry("nhn", "NHN"),
            Map.entry("wrtn", "뤼튼"),
            Map.entry("upstage", "업스테이지"),
            Map.entry("socar", "쏘카")
    );

    private static final Map<String, String> CATEGORY_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("백엔드", "백엔드"),
            Map.entry("프론트엔드", "프론트엔드"),
            Map.entry("풀스택", "풀스택"),
            Map.entry("모바일", "모바일"),
            Map.entry("AI/ML", "AI/ML"),
            Map.entry("데이터엔지니어링", "데이터엔지니어링"),
            Map.entry("DevOps/인프라", "DevOps/인프라"),
            Map.entry("보안", "보안"),
            Map.entry("QA/테스트", "QA/테스트"),
            Map.entry("임베디드", "임베디드"),
            Map.entry("기타개발", "기타개발"),
            Map.entry("PM/PO", "PM/PO"),
            Map.entry("서비스기획", "서비스기획")
    );

    public TechCardResponse getTechCards() {
        List<CardItem> cards = new ArrayList<>();

        // 1. 카테고리별 신규 공고 카드
        CardItem categoryCard = generateCategoryCard();
        if (categoryCard != null) {
            cards.add(categoryCard);
        }

        // 2. 신규 공고 현황 카드 (관련 공고 목록 포함)
        CardItem newJobsCard = generateNewJobsCard();
        if (newJobsCard != null) {
            cards.add(newJobsCard);
        }

        // 3. 외부 뉴스 카드 (HackerNews 최신 1장)
        Optional<TechCard> externalCard = techCardRepository.findTop1BySourceOrderByCreatedAtDesc(ContentSource.HACKERNEWS);
        externalCard.ifPresent(card -> cards.add(new CardItem(
                card.getId(),
                card.getSource().name(),
                "요즘 뜨는 트렌드",
                card.getHeadline(),
                card.getSubtext(),
                card.getOriginalUrl(),
                card.getPublishedAt(),
                card.getCreatedAt(),
                null
        )));

        return new TechCardResponse(cards);
    }

    private CardItem generateCategoryCard() {
        @SuppressWarnings("unchecked")
        List<Tuple> results = entityManager.createNativeQuery("""
                SELECT job_category AS category, COUNT(*) AS cnt
                FROM private_job_postings
                WHERE DATE(created_at) = CURRENT_DATE
                  AND job_category IS NOT NULL
                  AND job_category NOT IN ('비대상', '미분류')
                GROUP BY job_category
                ORDER BY cnt DESC
                LIMIT 3
                """, Tuple.class)
                .getResultList();

        if (results.isEmpty()) {
            return null;
        }

        String topCategory = (String) results.get(0).get("category");
        long topCount = ((Number) results.get(0).get("cnt")).longValue();
        String displayCategory = CATEGORY_DISPLAY_NAMES.getOrDefault(topCategory, topCategory);

        String headline = "오늘 %s 공고 %d건이 새로 올라왔어요".formatted(displayCategory, topCount);

        String subtext;
        if (results.size() >= 2) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < results.size(); i++) {
                String cat = (String) results.get(i).get("category");
                long cnt = ((Number) results.get(i).get("cnt")).longValue();
                String display = CATEGORY_DISPLAY_NAMES.getOrDefault(cat, cat);
                if (i > 1) sb.append(", ");
                sb.append("%s %d건".formatted(display, cnt));
            }
            sb.append("도 함께 수집됐어요");
            subtext = sb.toString();
        } else {
            subtext = "새로운 공고를 확인해보세요";
        }

        // 상위 카테고리의 관련 공고 목록 (최신 5건)
        @SuppressWarnings("unchecked")
        List<Tuple> jobResults = entityManager.createNativeQuery("""
                SELECT id, company, title
                FROM private_job_postings
                WHERE DATE(created_at) = CURRENT_DATE
                  AND job_category = :category
                ORDER BY created_at DESC
                LIMIT 5
                """, Tuple.class)
                .setParameter("category", topCategory)
                .getResultList();

        List<RelatedJob> relatedJobs = jobResults.stream()
                .map(row -> new RelatedJob(
                        ((Number) row.get("id")).longValue(),
                        "PRIVATE",
                        COMPANY_DISPLAY_NAMES.getOrDefault((String) row.get("company"), (String) row.get("company")),
                        (String) row.get("title")
                ))
                .toList();

        return new CardItem(null, ContentSource.INTERNAL.name(), "채용 트렌드", headline, subtext, null, null, LocalDateTime.now(), relatedJobs);
    }

    private CardItem generateNewJobsCard() {
        // 오늘 수집된 총 건수
        @SuppressWarnings("unchecked")
        List<Object> countResult = entityManager.createNativeQuery("""
                SELECT COUNT(*) FROM private_job_postings
                WHERE DATE(created_at) = CURRENT_DATE
                """)
                .getResultList();

        long totalCount = countResult.isEmpty() ? 0 : ((Number) countResult.get(0)).longValue();
        if (totalCount == 0) {
            return null;
        }

        // 관련 공고 목록 (최신 5건)
        @SuppressWarnings("unchecked")
        List<Tuple> jobResults = entityManager.createNativeQuery("""
                SELECT id, company, title
                FROM private_job_postings
                WHERE DATE(created_at) = CURRENT_DATE
                ORDER BY created_at DESC
                LIMIT 5
                """, Tuple.class)
                .getResultList();

        List<RelatedJob> relatedJobs = jobResults.stream()
                .map(row -> new RelatedJob(
                        ((Number) row.get("id")).longValue(),
                        "PRIVATE",
                        COMPANY_DISPLAY_NAMES.getOrDefault((String) row.get("company"), (String) row.get("company")),
                        (String) row.get("title")
                ))
                .toList();

        return new CardItem(
                null,
                ContentSource.INTERNAL.name(),
                "신규 공고",
                "오늘 새로 올라온 공고가 %d건 있어요".formatted(totalCount),
                "새로운 채용 기회를 확인해보세요",
                null,
                null,
                LocalDateTime.now(),
                relatedJobs
        );
    }
}

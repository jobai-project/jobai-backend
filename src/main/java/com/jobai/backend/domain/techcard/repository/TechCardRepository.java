package com.jobai.backend.domain.techcard.repository;

import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.entity.TechCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TechCardRepository extends JpaRepository<TechCard, Long> {

    /** 오늘 수집된 외부 뉴스 카드를 랜덤 순서로 최대 2건 조회한다. INTERNAL 소스는 제외. */
    @Query(value = """
            SELECT * FROM tech_cards
            WHERE source != 'INTERNAL'
              AND created_at >= CURRENT_DATE
              AND created_at < CURRENT_DATE + 1
            ORDER BY RANDOM()
            LIMIT 2
            """, nativeQuery = true)
    List<TechCard> findTodayNewsCardsRandom();
}

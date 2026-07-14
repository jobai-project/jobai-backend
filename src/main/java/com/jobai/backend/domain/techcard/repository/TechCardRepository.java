package com.jobai.backend.domain.techcard.repository;

import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.entity.TechCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TechCardRepository extends JpaRepository<TechCard, Long> {

    @Query(value = """
            SELECT * FROM tech_cards
            WHERE source != 'INTERNAL'
              AND created_at >= CURRENT_DATE
              AND created_at < CURRENT_DATE + 1
            ORDER BY RANDOM()
            """, nativeQuery = true)
    List<TechCard> findTodayNewsCardsRandom();
}

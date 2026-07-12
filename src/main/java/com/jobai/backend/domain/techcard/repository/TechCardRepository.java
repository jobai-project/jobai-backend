package com.jobai.backend.domain.techcard.repository;

import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.entity.TechCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TechCardRepository extends JpaRepository<TechCard, Long> {

    Optional<TechCard> findTop1BySourceOrderByCreatedAtDesc(ContentSource source);
}

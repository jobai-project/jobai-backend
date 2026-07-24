package com.jobai.backend.domain.member.repository;

import com.jobai.backend.domain.member.entity.MatchScore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchScoreRepository extends JpaRepository<MatchScore, Long> {

    void deleteByMemberId(Long memberId);
}

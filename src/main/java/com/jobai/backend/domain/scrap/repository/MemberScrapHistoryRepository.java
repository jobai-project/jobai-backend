package com.jobai.backend.domain.scrap.repository;

import com.jobai.backend.domain.scrap.entity.MemberScrapHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberScrapHistoryRepository extends JpaRepository<MemberScrapHistory, Long> {

    Optional<MemberScrapHistory> findByMemberEmailAndSourceAndSourceId(String email, String source, Long sourceId);

    List<MemberScrapHistory> findByMemberEmailOrderByScrappedAtDesc(String email);

    void deleteByMemberEmailAndSourceAndSourceId(String email, String source, Long sourceId);
}

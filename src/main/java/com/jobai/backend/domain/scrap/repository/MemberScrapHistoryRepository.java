package com.jobai.backend.domain.scrap.repository;

import com.jobai.backend.domain.scrap.entity.MemberScrapHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberScrapHistoryRepository extends JpaRepository<MemberScrapHistory, Long> {

    Optional<MemberScrapHistory> findByMemberEmailAndSourceAndSourceId(String email, String source, Long sourceId);

    List<MemberScrapHistory> findByMemberEmailOrderByScrappedAtDesc(String email);

    void deleteByMemberEmailAndSourceAndSourceId(String email, String source, Long sourceId);

    void deleteByMemberId(Long memberId);

    @Query("""
            SELECT h.id, h.source, h.sourceId, p.companyName, p.title, p.workRegion, p.recrutType, p.endDate, h.scrappedAt
            FROM MemberScrapHistory h
            JOIN PublicJobPosting p ON p.id = h.sourceId
            WHERE h.member.email = :email
              AND h.source = 'PUBLIC'
              AND p.endDate IS NOT NULL
              AND p.endDate >= :today
              AND (p.isClosed IS NULL OR p.isClosed = false)
            ORDER BY p.endDate ASC, h.scrappedAt DESC, h.sourceId DESC
            """)
    List<Object[]> findUpcomingPublicDeadlineScraps(
            @Param("email") String email,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @Query("""
            SELECT h.id, h.source, h.sourceId, p.company, p.title, p.location, p.employmentType, p.deadline, h.scrappedAt
            FROM MemberScrapHistory h
            JOIN PrivateJobPosting p ON p.id = h.sourceId
            WHERE h.member.email = :email
              AND h.source = 'PRIVATE'
              AND p.deadline IS NOT NULL
              AND p.deadline >= :today
              AND p.isClosed = false
            ORDER BY p.deadline ASC, h.scrappedAt DESC, h.sourceId DESC
            """)
    List<Object[]> findUpcomingPrivateDeadlineScraps(
            @Param("email") String email,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    @Query("""
            SELECT h.source, h.sourceId, p.title, p.companyName, COUNT(h.id), MAX(h.scrappedAt)
            FROM MemberScrapHistory h
            JOIN PublicJobPosting p ON p.id = h.sourceId
            WHERE h.source = 'PUBLIC'
              AND (p.isClosed IS NULL OR p.isClosed = false)
            GROUP BY h.source, h.sourceId, p.title, p.companyName
            ORDER BY COUNT(h.id) DESC, MAX(h.scrappedAt) DESC, h.sourceId DESC
            """)
    List<Object[]> findPopularPublicScraps(Pageable pageable);

    @Query("""
            SELECT h.source, h.sourceId, p.title, p.company, COUNT(h.id), MAX(h.scrappedAt)
            FROM MemberScrapHistory h
            JOIN PrivateJobPosting p ON p.id = h.sourceId
            WHERE h.source = 'PRIVATE'
              AND p.isClosed = false
            GROUP BY h.source, h.sourceId, p.title, p.company
            ORDER BY COUNT(h.id) DESC, MAX(h.scrappedAt) DESC, h.sourceId DESC
            """)
    List<Object[]> findPopularPrivateScraps(Pageable pageable);
}

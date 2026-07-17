package com.jobai.backend.domain.crawler.repository;

import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PrivateJobPostingRepository extends JpaRepository<PrivateJobPosting, Long> {

    // company + source_job_id 로 기존 공고 찾기 (upsert 판단용)
    Optional<PrivateJobPosting> findByCompanyAndSourceJobId(String company, String sourceJobId);

    // 특정 회사의 모든 공고 (마감 처리 시, 이번 수집과 비교용)
    List<PrivateJobPosting> findAllByCompany(String company);

    // 우리 택소노미가 아닌 공고 = 재분류 대상 (null, 미분류, 회사원본명 전부). Keyset pagination.
    @Query("""
        SELECT p FROM PrivateJobPosting p
        WHERE p.isClosed = false
          AND (p.jobCategory IS NULL OR p.jobCategory NOT IN :validLabels)
          AND p.id > :lastId
        """)
    Page<PrivateJobPosting> findNeedsClassification(
            @Param("validLabels") List<String> validLabels,
            @Param("lastId") Long lastId,
            Pageable pageable);

    // 특정 시각 이후 생성된 공고 조회 (신규 공고 export용)
    Page<PrivateJobPosting> findByCreatedAtAfter(LocalDateTime since, Pageable pageable);

    // jobCategory 있지만 employmentType 또는 experienceLevel이 null인 공고. Keyset pagination.
    @Query("""
        SELECT p FROM PrivateJobPosting p
        WHERE p.isClosed = false
          AND p.jobCategory IN :validLabels
          AND (p.employmentType IS NULL OR p.employmentType = ''
               OR p.experienceLevel IS NULL OR p.experienceLevel = '')
          AND p.id > :lastId
        """)
    Page<PrivateJobPosting> findNeedsEmploymentTypeClassification(
            @Param("validLabels") List<String> validLabels,
            @Param("lastId") Long lastId,
            Pageable pageable);

    // location이 있지만 아직 정규화된 지역 라벨이 아닌 공고. Keyset pagination.
    @Query("""
        SELECT p FROM PrivateJobPosting p
        WHERE p.isClosed = false
          AND p.location IS NOT NULL
          AND p.location <> ''
          AND p.location NOT IN :validRegionLabels
          AND p.id > :lastId
        """)
    Page<PrivateJobPosting> findNeedsRegionClassification(
            @Param("validRegionLabels") List<String> validRegionLabels,
            @Param("lastId") Long lastId,
            Pageable pageable);

    /** 배치 점수 산출: 활성 공고 중 유효 카테고리만 조회 */
    @Query("SELECT p FROM PrivateJobPosting p WHERE p.isClosed = false AND p.jobCategory IN :validCategories")
    List<PrivateJobPosting> findActiveByValidCategories(@Param("validCategories") List<String> validCategories);
}

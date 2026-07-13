package com.jobai.backend.domain.publicInstitution.repository;

import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Query("SELECT p FROM PublicJobPosting p WHERE p.pblntfNo = :pblntfNo")
    Optional<JobPosting> findByPblntfNo(@Param("pblntfNo") String pblntfNo);

    @Query("SELECT p FROM PublicJobPosting p WHERE p.id = :id")
    Optional<PublicJobPosting> findPublicJobPostingById(@Param("id") Long id);

    /** 매칭 점수 배치 산출용: 마감되지 않은 공기업 공고 전체 조회 */
    @Query("SELECT p FROM PublicJobPosting p WHERE p.isClosed IS NULL OR p.isClosed = false")
    List<PublicJobPosting> findActivePublicPostings();
}
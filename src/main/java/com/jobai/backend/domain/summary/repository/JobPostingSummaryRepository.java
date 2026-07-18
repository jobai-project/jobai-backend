package com.jobai.backend.domain.summary.repository;

import com.jobai.backend.domain.summary.entity.JobPostingSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 공고 요약 캐시 저장소. */
public interface JobPostingSummaryRepository extends JpaRepository<JobPostingSummary, Long> {

    /** 공고 ID로 캐시된 요약을 조회한다. */
    Optional<JobPostingSummary> findByJobPostingId(Long jobPostingId);
}

package com.jobai.backend.domain.crawler.repository;

import com.jobai.backend.domain.crawler.entity.JobPostingSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPostingSummaryRepository extends JpaRepository<JobPostingSummary, Long> {

    Optional<JobPostingSummary> findByJobPostingId(Long jobPostingId);
}

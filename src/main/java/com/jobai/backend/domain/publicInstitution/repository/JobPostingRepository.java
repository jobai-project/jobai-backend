package com.jobai.backend.domain.publicInstitution.repository;

import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    Optional<JobPosting> findByPblntfNo(String pblntfNo);
}
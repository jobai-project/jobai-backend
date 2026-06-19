package com.jobai.backend.domain.publicInstitution.repository;

import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    @Query("SELECT p FROM PublicJobPosting p WHERE p.pblntfNo = :pblntfNo")
    Optional<JobPosting> findByPblntfNo(@Param("pblntfNo") String pblntfNo);
}
package com.jobai.backend.domain.crawler.repository;

import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrivateJobPostingRepository extends JpaRepository<PrivateJobPosting, Long> {

    // company + source_job_id 로 기존 공고 찾기 (upsert 판단용)
    Optional<PrivateJobPosting> findByCompanyAndSourceJobId(String company, String sourceJobId);

    // 특정 회사의 모든 공고 (마감 처리 시, 이번 수집과 비교용)
    List<PrivateJobPosting> findAllByCompany(String company);
}

package com.jobai.backend.domain.home.repository;

import com.jobai.backend.domain.home.entity.PublicMatchScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicMatchScoreRepository extends JpaRepository<PublicMatchScore, Long> {

    /** 홈화면용: 특정 이력서에 대해 여러 공고의 점수를 벌크 조회한다. */
    List<PublicMatchScore> findByResumeIdAndPublicJobPostingIdIn(Long resumeId, List<Long> jobIds);

    /** 이력서 변경 시 기존 점수를 삭제한다. */
    void deleteByResumeId(Long resumeId);

    /** 배치 점수 산출: 특정 이력서의 기존 점수를 공고 ID 포함하여 조회 (신규/변경 판단용) */
    List<PublicMatchScore> findByResumeId(Long resumeId);
}

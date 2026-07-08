package com.jobai.backend.domain.home.repository;

import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrivateMatchScoreRepository extends JpaRepository<PrivateMatchScore, Long> {

    /** 홈화면용: 특정 이력서에 대해 여러 공고의 점수를 벌크 조회한다. */
    List<PrivateMatchScore> findByResumeIdAndPrivateJobPostingIdIn(Long resumeId, List<Long> jobIds);

    /** 이력서 변경 시 기존 점수를 삭제한다. */
    void deleteByResumeId(Long resumeId);
}

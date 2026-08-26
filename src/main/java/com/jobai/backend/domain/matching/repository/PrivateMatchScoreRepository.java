package com.jobai.backend.domain.matching.repository;

import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrivateMatchScoreRepository extends JpaRepository<PrivateMatchScore, Long> {

    /** 홈화면용: 특정 이력서에 대해 여러 공고의 점수를 벌크 조회한다. */
    List<PrivateMatchScore> findByResumeIdAndPrivateJobPostingIdIn(Long resumeId, List<Long> jobIds);

    /** 이력서 삭제 시 연결된 점수를 함께 삭제한다. */
    void deleteByResumeId(Long resumeId);

    /** 배치 점수 산출: 특정 이력서의 기존 점수를 공고 ID 포함하여 조회 (신규/변경 판단용) */
    List<PrivateMatchScore> findByResumeId(Long resumeId);

    /** Kafka Consumer 멱등성 체크: 해당 이력서-공고 조합의 점수가 이미 존재하는지 확인 */
    boolean existsByResumeIdAndPrivateJobPostingId(Long resumeId, Long privateJobPostingId);
}

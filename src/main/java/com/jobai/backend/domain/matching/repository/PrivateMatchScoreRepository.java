package com.jobai.backend.domain.matching.repository;

import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PrivateMatchScoreRepository extends JpaRepository<PrivateMatchScore, Long> {

    /** 홈화면용: 특정 이력서에 대해 여러 공고의 점수를 벌크 조회한다. */
    List<PrivateMatchScore> findByResumeIdAndPrivateJobPostingIdIn(Long resumeId, List<Long> jobIds);

    /** 이력서 삭제 시 연결된 점수를 함께 삭제한다. */
    void deleteByResumeId(Long resumeId);

    /** 배치 점수 산출: 특정 이력서의 기존 점수를 공고 ID 포함하여 조회 (신규/변경 판단용).
     *  JOIN FETCH로 privateJobPosting을 즉시 로딩하여 N+1 쿼리를 방지한다. */
    @Query("SELECT s FROM PrivateMatchScore s JOIN FETCH s.privateJobPosting WHERE s.resume.id = :resumeId")
    List<PrivateMatchScore> findByResumeId(@Param("resumeId") Long resumeId);

    /** 알림용: 임계값 이상이고 최근 등록된 공고의 점수만 JOIN FETCH로 조회한다. */
    @Query("SELECT s FROM PrivateMatchScore s JOIN FETCH s.privateJobPosting p " +
           "WHERE s.resume.id = :resumeId AND s.score >= :threshold AND p.createdAt >= :since")
    List<PrivateMatchScore> findNotificationTargets(
            @Param("resumeId") Long resumeId,
            @Param("threshold") int threshold,
            @Param("since") LocalDateTime since);

    /** Kafka Consumer 멱등성 체크: 해당 이력서-공고 조합의 점수가 이미 존재하는지 확인 */
    boolean existsByResumeIdAndPrivateJobPostingId(Long resumeId, Long privateJobPostingId);
}

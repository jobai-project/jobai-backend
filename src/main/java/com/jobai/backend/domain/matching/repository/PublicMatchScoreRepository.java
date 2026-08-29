package com.jobai.backend.domain.matching.repository;

import com.jobai.backend.domain.matching.entity.PublicMatchScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PublicMatchScoreRepository extends JpaRepository<PublicMatchScore, Long> {

    /** 홈화면용: 특정 이력서에 대해 여러 공고의 점수를 벌크 조회한다. */
    List<PublicMatchScore> findByResumeIdAndPublicJobPostingIdIn(Long resumeId, List<Long> jobIds);

    /** 이력서 삭제 시 연결된 점수를 함께 삭제한다. */
    void deleteByResumeId(Long resumeId);

    /** 배치 점수 산출: 특정 이력서의 기존 점수를 공고 ID 포함하여 조회 (신규/변경 판단용).
     *  JOIN FETCH로 publicJobPosting을 즉시 로딩하여 N+1 쿼리를 방지한다. */
    @Query("SELECT s FROM PublicMatchScore s JOIN FETCH s.publicJobPosting WHERE s.resume.id = :resumeId")
    List<PublicMatchScore> findByResumeId(@Param("resumeId") Long resumeId);

    /** 알림용: 임계값 이상이고 최근 등록된 공고의 점수만 JOIN FETCH로 조회한다. */
    @Query("SELECT s FROM PublicMatchScore s JOIN FETCH s.publicJobPosting p " +
           "WHERE s.resume.id = :resumeId AND s.score >= :threshold AND p.createdAt >= :since")
    List<PublicMatchScore> findNotificationTargets(
            @Param("resumeId") Long resumeId,
            @Param("threshold") int threshold,
            @Param("since") LocalDateTime since);
}

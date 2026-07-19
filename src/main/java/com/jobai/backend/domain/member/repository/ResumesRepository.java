package com.jobai.backend.domain.member.repository;

import com.jobai.backend.domain.member.entity.Resumes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ResumesRepository extends JpaRepository<Resumes, Long> {

    List<Resumes> findByMemberEmailOrderByUpdatedAtDescIdDesc(String email);

    Optional<Resumes> findByMemberEmailAndIsActiveTrue(String email);

    @Modifying
    @Query("UPDATE Resumes r SET r.isActive = false WHERE r.member.id = :memberId AND r.id <> :excludeId")
    void deactivateOthersByMemberId(Long memberId, Long excludeId);

    /** 배치 점수 산출(사기업): 활성이면서 임베딩이 생성된 이력서 전체 조회 (Member JOIN FETCH) */
    @Query("SELECT r FROM Resumes r JOIN FETCH r.member WHERE r.isActive = true AND r.embedding IS NOT NULL")
    List<Resumes> findAllActiveWithEmbedding();

    /** 배치 임베딩 생성: 활성이면서 텍스트는 있지만 임베딩이 없는 이력서 조회 */
    @Query("SELECT r FROM Resumes r WHERE r.isActive = true AND r.extractedText IS NOT NULL AND r.extractedText <> '' AND r.embedding IS NULL")
    List<Resumes> findActiveWithoutEmbedding();

    /** 배치 점수 산출(공기업): 활성이면서 NCS 임베딩이 생성된 이력서 전체 조회 (Member JOIN FETCH) */
    @Query("SELECT r FROM Resumes r JOIN FETCH r.member WHERE r.isActive = true AND r.ncsEmbedding IS NOT NULL")
    List<Resumes> findAllActiveWithNcsEmbedding();
}

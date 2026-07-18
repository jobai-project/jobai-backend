package com.jobai.backend.domain.search.repository;

import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.global.model.JobSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobEmbeddingRepository extends JpaRepository<JobEmbedding, Long> {

    Optional<JobEmbedding> findBySourceAndSourceId(JobSource source, Long sourceId);

    @Modifying
    @Query("DELETE FROM JobEmbedding e WHERE e.source = :source AND e.sourceId = :sourceId")
    void deleteBySourceAndSourceId(@Param("source") JobSource source, @Param("sourceId") Long sourceId);

    /**
     * 임베딩이 아직 없는 PrivateJobPosting ID 목록 조회
     */
    @Query(value = """
        SELECT p.id FROM private_job_postings p
        WHERE p.is_closed = false
          AND p.job_category IS NOT NULL
          AND p.job_category NOT IN ('미분류', '비대상')
          AND NOT EXISTS (
              SELECT 1 FROM job_embeddings e
              WHERE e.source = 'PRIVATE' AND e.source_id = p.id
          )
        ORDER BY p.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findPrivateIdsWithoutEmbedding(@Param("limit") int limit);

    /**
     * 임베딩이 아직 없는 PublicJobPosting ID 목록 조회
     */
    @Query(value = """
        SELECT p.id FROM public_job_postings p
        WHERE (p.is_closed IS NULL OR p.is_closed = false)
          AND NOT EXISTS (
              SELECT 1 FROM job_embeddings e
              WHERE e.source = 'PUBLIC' AND e.source_id = p.id
          )
        ORDER BY p.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findPublicIdsWithoutEmbedding(@Param("limit") int limit);
}

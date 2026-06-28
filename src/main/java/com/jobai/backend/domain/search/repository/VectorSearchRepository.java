package com.jobai.backend.domain.search.repository;

import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.service.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class VectorSearchRepository {

    private final EntityManager em;

    /**
     * 벡터 검색 결과를 유사도 거리와 함께 담는 내부 record.
     * distance가 작을수록 유사도가 높다.
     */
    public record ScoredJob(JobSummary job, double distance) {}

    public List<ScoredJob> searchPrivateByVector(float[] queryEmbedding, double threshold,
                                                   SearchCondition condition, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.title, p.company, p.location, p.job_category,
                   p.employment_type, p.apply_url, p.deadline, p.created_at,
                   (e.embedding <=> cast(:queryEmbedding as vector)) AS distance
            FROM private_job_postings p
            JOIN job_embeddings e ON e.source = 'PRIVATE' AND e.source_id = p.id
            WHERE p.is_closed = false
              AND p.job_category IS NOT NULL
              AND p.job_category NOT IN ('미분류', '비대상')
              AND (e.embedding <=> cast(:queryEmbedding as vector)) < :threshold
            """);

        boolean hasCategories = condition.categories() != null && !condition.categories().isEmpty();
        if (hasCategories) {
            sql.append(" AND p.job_category IN (:categories)");
        }
        if (hasText(condition.location())) {
            sql.append(" AND LOWER(p.location) LIKE :locationPattern");
        }

        sql.append(" ORDER BY distance LIMIT :limit OFFSET :offset");

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("queryEmbedding", toVectorString(queryEmbedding));
        query.setParameter("threshold", threshold);
        if (hasCategories) {
            query.setParameter("categories", condition.categories());
        }
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        query.setParameter("limit", limit);
        query.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> new ScoredJob(
                        new JobSummary(
                                ((Number) row[0]).longValue(),
                                "PRIVATE",
                                (String) row[1],
                                (String) row[2],
                                (String) row[3],
                                (String) row[4],
                                (String) row[5],
                                (String) row[6],
                                row[7] != null ? ((java.sql.Date) row[7]).toLocalDate() : null,
                                row[8] != null ? ((java.sql.Timestamp) row[8]).toLocalDateTime() : null
                        ),
                        ((Number) row[9]).doubleValue()
                ))
                .toList();
    }

    public List<ScoredJob> searchPublicByVector(float[] queryEmbedding, double threshold,
                                                  SearchCondition condition, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT jp.id, jp.title, jp.company_name, jp.work_region,
                   jp.recrut_type, p.apply_link, jp.end_date, jp.created_at,
                   (e.embedding <=> cast(:queryEmbedding as vector)) AS distance
            FROM public_job_postings p
            JOIN job_postings jp ON jp.id = p.id
            JOIN job_embeddings e ON e.source = 'PUBLIC' AND e.source_id = p.id
            WHERE (p.is_closed IS NULL OR p.is_closed = false)
              AND (e.embedding <=> cast(:queryEmbedding as vector)) < :threshold
            """);

        if (hasText(condition.location())) {
            sql.append(" AND LOWER(jp.work_region) LIKE :locationPattern");
        }
        if (hasText(condition.experience())) {
            sql.append(" AND LOWER(jp.work_experience) LIKE :experiencePattern");
        }

        sql.append(" ORDER BY distance LIMIT :limit OFFSET :offset");

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("queryEmbedding", toVectorString(queryEmbedding));
        query.setParameter("threshold", threshold);
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        if (hasText(condition.experience())) {
            query.setParameter("experiencePattern", "%" + condition.experience().trim().toLowerCase() + "%");
        }
        query.setParameter("limit", limit);
        query.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> new ScoredJob(
                        new JobSummary(
                                ((Number) row[0]).longValue(),
                                "PUBLIC",
                                (String) row[1],
                                (String) row[2],
                                (String) row[3],
                                null,
                                (String) row[4],
                                (String) row[5],
                                row[6] != null ? ((java.sql.Date) row[6]).toLocalDate() : null,
                                row[7] != null ? ((java.sql.Timestamp) row[7]).toLocalDateTime() : null
                        ),
                        ((Number) row[8]).doubleValue()
                ))
                .toList();
    }

    public long countPrivateByVector(float[] queryEmbedding, double threshold, SearchCondition condition) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM private_job_postings p
            JOIN job_embeddings e ON e.source = 'PRIVATE' AND e.source_id = p.id
            WHERE p.is_closed = false
              AND p.job_category IS NOT NULL
              AND p.job_category NOT IN ('미분류', '비대상')
              AND (e.embedding <=> cast(:queryEmbedding as vector)) < :threshold
            """);

        boolean hasCategories = condition.categories() != null && !condition.categories().isEmpty();
        if (hasCategories) {
            sql.append(" AND p.job_category IN (:categories)");
        }
        if (hasText(condition.location())) {
            sql.append(" AND LOWER(p.location) LIKE :locationPattern");
        }

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("queryEmbedding", toVectorString(queryEmbedding));
        query.setParameter("threshold", threshold);
        if (hasCategories) {
            query.setParameter("categories", condition.categories());
        }
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }

        return ((Number) query.getSingleResult()).longValue();
    }

    public long countPublicByVector(float[] queryEmbedding, double threshold, SearchCondition condition) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM public_job_postings p
            JOIN job_postings jp ON jp.id = p.id
            JOIN job_embeddings e ON e.source = 'PUBLIC' AND e.source_id = p.id
            WHERE (p.is_closed IS NULL OR p.is_closed = false)
              AND (e.embedding <=> cast(:queryEmbedding as vector)) < :threshold
            """);

        if (hasText(condition.location())) {
            sql.append(" AND LOWER(jp.work_region) LIKE :locationPattern");
        }
        if (hasText(condition.experience())) {
            sql.append(" AND LOWER(jp.work_experience) LIKE :experiencePattern");
        }

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("queryEmbedding", toVectorString(queryEmbedding));
        query.setParameter("threshold", threshold);
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        if (hasText(condition.experience())) {
            query.setParameter("experiencePattern", "%" + condition.experience().trim().toLowerCase() + "%");
        }

        return ((Number) query.getSingleResult()).longValue();
    }

    private static String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

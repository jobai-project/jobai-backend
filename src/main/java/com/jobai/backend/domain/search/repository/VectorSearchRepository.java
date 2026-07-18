package com.jobai.backend.domain.search.repository;

import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.dto.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * pgvector 기반 벡터 유사도 검색 레포지토리.
 * 코사인 거리 연산자({@code <=>})를 사용하여 쿼리 벡터와 공고 임베딩 간 유사도를 비교한다.
 */
@Repository
@RequiredArgsConstructor
public class VectorSearchRepository {

    private final EntityManager em;

    /**
     * 벡터 검색 결과를 유사도 거리와 함께 담는 내부 record.
     * distance가 작을수록 유사도가 높다.
     */
    public record ScoredJob(JobSummary job, double distance) {}

    /** 사기업 공고를 벡터 유사도순으로 검색한다. 카테고리/지역 pre-filter를 적용한다. */
    public List<ScoredJob> searchPrivateByVector(float[] queryEmbedding, double threshold,
                                                   SearchCondition condition, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.title, p.company, p.location, p.job_category,
                   p.employment_type, p.apply_url, p.deadline, p.created_at,
                   (e.embedding <=> cast(:queryEmbedding as vector)) AS distance,
                   p.experience_level
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
        if (hasText(condition.company())) {
            sql.append(" AND p.company = :company");
        }
        if (hasText(condition.location())) {
            sql.append(" AND (LOWER(p.location) LIKE :locationPattern OR p.location IS NULL)");
        }
        boolean hasExpLevels = condition.experienceLevels() != null && !condition.experienceLevels().isEmpty();
        if (hasExpLevels) {
            sql.append(" AND (p.experience_level IN (:expLevels) OR p.experience_level IS NULL)");
        }
        boolean hasEmpTypes = condition.employmentTypes() != null && !condition.employmentTypes().isEmpty();
        if (hasEmpTypes) {
            sql.append(" AND (p.employment_type IN (:empTypes) OR p.employment_type IS NULL)");
        }

        sql.append(" ORDER BY distance LIMIT :limit OFFSET :offset");

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("queryEmbedding", toVectorString(queryEmbedding));
        query.setParameter("threshold", threshold);
        if (hasCategories) {
            query.setParameter("categories", condition.categories());
        }
        if (hasText(condition.company())) {
            query.setParameter("company", condition.company());
        }
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        if (hasExpLevels) {
            query.setParameter("expLevels", condition.experienceLevels());
        }
        if (hasEmpTypes) {
            query.setParameter("empTypes", condition.employmentTypes());
        }
        query.setParameter("limit", limit);
        query.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(row -> new ScoredJob(
                        JobSummary.of(
                                ((Number) row[0]).longValue(),
                                "PRIVATE",
                                (String) row[1],
                                (String) row[2],
                                (String) row[3],
                                (String) row[4],
                                (String) row[5],
                                (String) row[10],
                                (String) row[6],
                                row[7] != null ? ((java.sql.Date) row[7]).toLocalDate() : null,
                                row[8] != null ? ((java.sql.Timestamp) row[8]).toLocalDateTime() : null,
                                determineMatchType(condition, (String) row[3], (String) row[10], (String) row[5])
                        ),
                        ((Number) row[9]).doubleValue()
                ))
                .toList();
    }

    /** 공기업 공고를 벡터 유사도순으로 검색한다. 지역/경력 pre-filter를 적용한다. */
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
                        JobSummary.of(
                                ((Number) row[0]).longValue(),
                                "PUBLIC",
                                (String) row[1],
                                (String) row[2],
                                (String) row[3],
                                null,
                                (String) row[4],
                                null,
                                (String) row[5],
                                row[6] != null ? ((java.sql.Date) row[6]).toLocalDate() : null,
                                row[7] != null ? ((java.sql.Timestamp) row[7]).toLocalDateTime() : null,
                                determinePublicMatchType(condition, (String) row[3], (String) row[4])
                        ),
                        ((Number) row[8]).doubleValue()
                ))
                .toList();
    }

    /** 벡터 유사도 threshold를 만족하는 사기업 공고 수를 반환한다. */
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
        if (hasText(condition.company())) {
            sql.append(" AND p.company = :company");
        }
        if (hasText(condition.location())) {
            sql.append(" AND (LOWER(p.location) LIKE :locationPattern OR p.location IS NULL)");
        }
        boolean hasExpLevels = condition.experienceLevels() != null && !condition.experienceLevels().isEmpty();
        if (hasExpLevels) {
            sql.append(" AND (p.experience_level IN (:expLevels) OR p.experience_level IS NULL)");
        }
        boolean hasEmpTypes = condition.employmentTypes() != null && !condition.employmentTypes().isEmpty();
        if (hasEmpTypes) {
            sql.append(" AND (p.employment_type IN (:empTypes) OR p.employment_type IS NULL)");
        }

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("queryEmbedding", toVectorString(queryEmbedding));
        query.setParameter("threshold", threshold);
        if (hasCategories) {
            query.setParameter("categories", condition.categories());
        }
        if (hasText(condition.company())) {
            query.setParameter("company", condition.company());
        }
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        if (hasExpLevels) {
            query.setParameter("expLevels", condition.experienceLevels());
        }
        if (hasEmpTypes) {
            query.setParameter("empTypes", condition.employmentTypes());
        }

        return ((Number) query.getSingleResult()).longValue();
    }

    /** 벡터 유사도 threshold를 만족하는 공기업 공고 수를 반환한다. */
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

    private static String determineMatchType(SearchCondition condition,
                                               String actualLocation,
                                               String actualExpLevel, String actualEmpType) {
        if (hasText(condition.location()) && (actualLocation == null
                || !actualLocation.toLowerCase().contains(condition.location().trim().toLowerCase()))) {
            return "SIMILAR";
        }
        boolean hasExpFilter = condition.experienceLevels() != null && !condition.experienceLevels().isEmpty();
        if (hasExpFilter && (actualExpLevel == null
                || "미확인".equals(actualExpLevel)
                || !condition.experienceLevels().contains(actualExpLevel))) {
            return "SIMILAR";
        }
        boolean hasEmpFilter = condition.employmentTypes() != null && !condition.employmentTypes().isEmpty();
        if (hasEmpFilter && (actualEmpType == null
                || "미확인".equals(actualEmpType)
                || !condition.employmentTypes().contains(actualEmpType))) {
            return "SIMILAR";
        }
        return "EXACT";
    }

    private static String determinePublicMatchType(SearchCondition condition,
                                                     String actualLocation, String actualRecrutType) {
        if (hasText(condition.location()) && (actualLocation == null
                || !actualLocation.toLowerCase().contains(condition.location().trim().toLowerCase()))) {
            return "SIMILAR";
        }
        boolean hasEmpFilter = condition.employmentTypes() != null && !condition.employmentTypes().isEmpty();
        if (hasEmpFilter && (actualRecrutType == null || !condition.employmentTypes().contains(actualRecrutType))) {
            return "SIMILAR";
        }
        return "EXACT";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

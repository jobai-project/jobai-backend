package com.jobai.backend.domain.search.repository;

import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.service.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class JobSearchRepository {

    private final EntityManager em;

    private static final List<String> EXCLUDED_CATEGORIES = List.of("미분류", "비대상");

    public List<JobSummary> searchPrivate(SearchCondition condition, int offset, int limit) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM PrivateJobPosting p WHERE p.isClosed = false"
                + " AND p.jobCategory IS NOT NULL AND p.jobCategory NOT IN :excludedCategories");

        List<String> predicates = new ArrayList<>();
        boolean hasCategory = condition.categories() != null && !condition.categories().isEmpty();
        List<String> sanitizedTitleKw = sanitizeKeywords(condition.titleKeywords());
        boolean hasTitleKeywords = !sanitizedTitleKw.isEmpty();

        if (hasCategory || hasTitleKeywords) {
            List<String> orClauses = new ArrayList<>();
            if (hasCategory) {
                orClauses.add("p.jobCategory IN :categories");
            }
            if (hasTitleKeywords) {
                for (int i = 0; i < sanitizedTitleKw.size(); i++) {
                    orClauses.add("LOWER(p.title) LIKE :titleKw" + i);
                }
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        if (hasText(condition.location())) {
            predicates.add("(LOWER(p.location) LIKE :locationPattern OR p.location IS NULL)");
        }
        boolean hasExpLevels = condition.experienceLevels() != null && !condition.experienceLevels().isEmpty();
        if (hasExpLevels) {
            predicates.add("(p.experienceLevel IN :expLevels OR p.experienceLevel IS NULL)");
        }
        boolean hasEmpTypes = condition.employmentTypes() != null && !condition.employmentTypes().isEmpty();
        if (hasEmpTypes) {
            predicates.add("(p.employmentType IN :empTypes OR p.employmentType IS NULL)");
        }

        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<?> query = em.createQuery(jpql.toString(),
                com.jobai.backend.domain.crawler.entity.PrivateJobPosting.class);

        query.setParameter("excludedCategories", EXCLUDED_CATEGORIES);
        if (hasCategory) {
            query.setParameter("categories", condition.categories());
        }
        if (hasTitleKeywords) {
            for (int i = 0; i < sanitizedTitleKw.size(); i++) {
                query.setParameter("titleKw" + i, "%" + sanitizedTitleKw.get(i).toLowerCase() + "%");
            }
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

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<com.jobai.backend.domain.crawler.entity.PrivateJobPosting> results =
                (List<com.jobai.backend.domain.crawler.entity.PrivateJobPosting>) query.getResultList();

        return results.stream()
                .map(p -> new JobSummary(
                        p.getId(),
                        "PRIVATE",
                        p.getTitle(),
                        p.getCompany(),
                        p.getLocation(),
                        p.getJobCategory(),
                        p.getEmploymentType(),
                        p.getApplyUrl(),
                        p.getDeadline(),
                        p.getCreatedAt(),
                        determineMatchType(condition, p.getLocation(), p.getExperienceLevel(), p.getEmploymentType())
                ))
                .toList();
    }

    public List<JobSummary> searchPublic(SearchCondition condition, int offset, int limit) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM PublicJobPosting p WHERE (p.isClosed IS NULL OR p.isClosed = false)");

        List<String> predicates = new ArrayList<>();

        // PublicJobPosting에는 jobCategory가 없으므로 카테고리 라벨도 title/jobRole LIKE로 검색
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(sanitizeKeywords(condition.titleKeywords()));
        if (condition.categories() != null) {
            condition.categories().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(allKeywords::add);
        }

        if (!allKeywords.isEmpty()) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < allKeywords.size(); i++) {
                orClauses.add("LOWER(p.title) LIKE :pubKw" + i);
                orClauses.add("LOWER(p.jobRole) LIKE :pubKw" + i);
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        if (hasText(condition.location())) {
            predicates.add("LOWER(p.workRegion) LIKE :locationPattern");
        }
        if (hasText(condition.experience())) {
            predicates.add("LOWER(p.workExperience) LIKE :experiencePattern");
        }

        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<?> query = em.createQuery(jpql.toString(),
                com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting.class);

        if (!allKeywords.isEmpty()) {
            for (int i = 0; i < allKeywords.size(); i++) {
                query.setParameter("pubKw" + i, "%" + allKeywords.get(i).toLowerCase() + "%");
            }
        }
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        if (hasText(condition.experience())) {
            query.setParameter("experiencePattern", "%" + condition.experience().trim().toLowerCase() + "%");
        }

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting> results =
                (List<com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting>) query.getResultList();

        return results.stream()
                .map(p -> new JobSummary(
                        p.getId(),
                        "PUBLIC",
                        p.getTitle(),
                        p.getCompanyName(),
                        p.getWorkRegion(),
                        null,
                        p.getRecrutType(),
                        p.getApplyLink(),
                        p.getEndDate(),
                        p.getCreatedAt(),
                        "EXACT"
                ))
                .toList();
    }

    public long countPrivate(SearchCondition condition) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(p) FROM PrivateJobPosting p WHERE p.isClosed = false"
                + " AND p.jobCategory IS NOT NULL AND p.jobCategory NOT IN :excludedCategories");

        List<String> predicates = new ArrayList<>();
        boolean hasCategory = condition.categories() != null && !condition.categories().isEmpty();
        List<String> sanitizedTitleKw = sanitizeKeywords(condition.titleKeywords());
        boolean hasTitleKeywords = !sanitizedTitleKw.isEmpty();

        if (hasCategory || hasTitleKeywords) {
            List<String> orClauses = new ArrayList<>();
            if (hasCategory) {
                orClauses.add("p.jobCategory IN :categories");
            }
            if (hasTitleKeywords) {
                for (int i = 0; i < sanitizedTitleKw.size(); i++) {
                    orClauses.add("LOWER(p.title) LIKE :titleKw" + i);
                }
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        if (hasText(condition.location())) {
            predicates.add("(LOWER(p.location) LIKE :locationPattern OR p.location IS NULL)");
        }
        boolean hasExpLevels = condition.experienceLevels() != null && !condition.experienceLevels().isEmpty();
        if (hasExpLevels) {
            predicates.add("(p.experienceLevel IN :expLevels OR p.experienceLevel IS NULL)");
        }
        boolean hasEmpTypes = condition.employmentTypes() != null && !condition.employmentTypes().isEmpty();
        if (hasEmpTypes) {
            predicates.add("(p.employmentType IN :empTypes OR p.employmentType IS NULL)");
        }

        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }

        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);

        query.setParameter("excludedCategories", EXCLUDED_CATEGORIES);
        if (hasCategory) {
            query.setParameter("categories", condition.categories());
        }
        if (hasTitleKeywords) {
            for (int i = 0; i < sanitizedTitleKw.size(); i++) {
                query.setParameter("titleKw" + i, "%" + sanitizedTitleKw.get(i).toLowerCase() + "%");
            }
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

        return query.getSingleResult();
    }

    public long countPublic(SearchCondition condition) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(p) FROM PublicJobPosting p WHERE (p.isClosed IS NULL OR p.isClosed = false)");

        List<String> predicates = new ArrayList<>();

        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(sanitizeKeywords(condition.titleKeywords()));
        if (condition.categories() != null) {
            condition.categories().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(allKeywords::add);
        }

        if (!allKeywords.isEmpty()) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < allKeywords.size(); i++) {
                orClauses.add("LOWER(p.title) LIKE :pubKw" + i);
                orClauses.add("LOWER(p.jobRole) LIKE :pubKw" + i);
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        if (hasText(condition.location())) {
            predicates.add("LOWER(p.workRegion) LIKE :locationPattern");
        }
        if (hasText(condition.experience())) {
            predicates.add("LOWER(p.workExperience) LIKE :experiencePattern");
        }

        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }

        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);

        if (!allKeywords.isEmpty()) {
            for (int i = 0; i < allKeywords.size(); i++) {
                query.setParameter("pubKw" + i, "%" + allKeywords.get(i).toLowerCase() + "%");
            }
        }
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        if (hasText(condition.experience())) {
            query.setParameter("experiencePattern", "%" + condition.experience().trim().toLowerCase() + "%");
        }

        return query.getSingleResult();
    }

    private static String determineMatchType(SearchCondition condition,
                                               String actualLocation,
                                               String actualExpLevel, String actualEmpType) {
        if (hasText(condition.location()) && (actualLocation == null
                || !actualLocation.toLowerCase().contains(condition.location().trim().toLowerCase()))) {
            return "SIMILAR";
        }
        boolean hasExpFilter = condition.experienceLevels() != null && !condition.experienceLevels().isEmpty();
        boolean hasEmpFilter = condition.employmentTypes() != null && !condition.employmentTypes().isEmpty();

        if (hasExpFilter && (actualExpLevel == null || !condition.experienceLevels().contains(actualExpLevel))) {
            return "SIMILAR";
        }
        if (hasEmpFilter && (actualEmpType == null || !condition.employmentTypes().contains(actualEmpType))) {
            return "SIMILAR";
        }
        return "EXACT";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> sanitizeKeywords(List<String> keywords) {
        if (keywords == null) return List.of();
        return keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}

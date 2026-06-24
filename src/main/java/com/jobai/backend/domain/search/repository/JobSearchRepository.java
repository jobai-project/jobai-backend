package com.jobai.backend.domain.search.repository;

import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.service.SearchCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

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
        boolean hasTitleKeywords = condition.titleKeywords() != null && !condition.titleKeywords().isEmpty();

        if (hasCategory || hasTitleKeywords) {
            List<String> orClauses = new ArrayList<>();
            if (hasCategory) {
                orClauses.add("p.jobCategory IN :categories");
            }
            if (hasTitleKeywords) {
                for (int i = 0; i < condition.titleKeywords().size(); i++) {
                    orClauses.add("LOWER(p.title) LIKE :titleKw" + i);
                }
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        if (condition.location() != null) {
            predicates.add("LOWER(p.location) LIKE :locationPattern");
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
            for (int i = 0; i < condition.titleKeywords().size(); i++) {
                query.setParameter("titleKw" + i, "%" + condition.titleKeywords().get(i).toLowerCase() + "%");
            }
        }
        if (condition.location() != null) {
            query.setParameter("locationPattern", "%" + condition.location().toLowerCase() + "%");
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
                        p.getCreatedAt()
                ))
                .toList();
    }

    public List<JobSummary> searchPublic(SearchCondition condition, int offset, int limit) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM PublicJobPosting p WHERE (p.isClosed IS NULL OR p.isClosed = false)");

        List<String> predicates = new ArrayList<>();
        boolean hasTitleKeywords = condition.titleKeywords() != null && !condition.titleKeywords().isEmpty();
        boolean hasCategory = condition.categories() != null && !condition.categories().isEmpty();

        // PublicJobPosting에는 jobCategory가 없으므로 카테고리 라벨도 title/jobRole LIKE로 검색
        List<String> allKeywords = new ArrayList<>();
        if (hasTitleKeywords) {
            allKeywords.addAll(condition.titleKeywords());
        }
        if (hasCategory) {
            allKeywords.addAll(condition.categories());
        }

        if (!allKeywords.isEmpty()) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < allKeywords.size(); i++) {
                orClauses.add("LOWER(p.title) LIKE :pubKw" + i);
                orClauses.add("LOWER(p.jobRole) LIKE :pubKw" + i);
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        if (condition.location() != null) {
            predicates.add("LOWER(p.workRegion) LIKE :locationPattern");
        }
        if (condition.experience() != null) {
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
        if (condition.location() != null) {
            query.setParameter("locationPattern", "%" + condition.location().toLowerCase() + "%");
        }
        if (condition.experience() != null) {
            query.setParameter("experiencePattern", "%" + condition.experience().toLowerCase() + "%");
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
                        p.getCreatedAt()
                ))
                .toList();
    }

    public long countPrivate(SearchCondition condition) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(p) FROM PrivateJobPosting p WHERE p.isClosed = false"
                + " AND p.jobCategory IS NOT NULL AND p.jobCategory NOT IN :excludedCategories");

        List<String> predicates = new ArrayList<>();
        boolean hasCategory = condition.categories() != null && !condition.categories().isEmpty();
        boolean hasTitleKeywords = condition.titleKeywords() != null && !condition.titleKeywords().isEmpty();

        if (hasCategory || hasTitleKeywords) {
            List<String> orClauses = new ArrayList<>();
            if (hasCategory) {
                orClauses.add("p.jobCategory IN :categories");
            }
            if (hasTitleKeywords) {
                for (int i = 0; i < condition.titleKeywords().size(); i++) {
                    orClauses.add("LOWER(p.title) LIKE :titleKw" + i);
                }
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        if (condition.location() != null) {
            predicates.add("LOWER(p.location) LIKE :locationPattern");
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
            for (int i = 0; i < condition.titleKeywords().size(); i++) {
                query.setParameter("titleKw" + i, "%" + condition.titleKeywords().get(i).toLowerCase() + "%");
            }
        }
        if (condition.location() != null) {
            query.setParameter("locationPattern", "%" + condition.location().toLowerCase() + "%");
        }

        return query.getSingleResult();
    }

    public long countPublic(SearchCondition condition) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(p) FROM PublicJobPosting p WHERE (p.isClosed IS NULL OR p.isClosed = false)");

        List<String> predicates = new ArrayList<>();
        boolean hasTitleKeywords = condition.titleKeywords() != null && !condition.titleKeywords().isEmpty();
        boolean hasCategory = condition.categories() != null && !condition.categories().isEmpty();

        List<String> allKeywords = new ArrayList<>();
        if (hasTitleKeywords) {
            allKeywords.addAll(condition.titleKeywords());
        }
        if (hasCategory) {
            allKeywords.addAll(condition.categories());
        }

        if (!allKeywords.isEmpty()) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < allKeywords.size(); i++) {
                orClauses.add("LOWER(p.title) LIKE :pubKw" + i);
                orClauses.add("LOWER(p.jobRole) LIKE :pubKw" + i);
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        if (condition.location() != null) {
            predicates.add("LOWER(p.workRegion) LIKE :locationPattern");
        }
        if (condition.experience() != null) {
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
        if (condition.location() != null) {
            query.setParameter("locationPattern", "%" + condition.location().toLowerCase() + "%");
        }
        if (condition.experience() != null) {
            query.setParameter("experiencePattern", "%" + condition.experience().toLowerCase() + "%");
        }

        return query.getSingleResult();
    }
}

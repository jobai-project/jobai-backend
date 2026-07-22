package com.jobai.backend.domain.search.repository;

import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.dto.RequirementGroup;
import com.jobai.backend.domain.search.dto.SearchCondition;
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

        List<String> predicates = buildPrivatePredicates(condition);
        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<?> query = em.createQuery(jpql.toString(), PrivateJobPosting.class);
        bindPrivateParams(query, condition);
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<PrivateJobPosting> results = (List<PrivateJobPosting>) query.getResultList();

        return results.stream()
                .map(p -> JobSummary.of(
                        p.getId(), "PRIVATE", p.getTitle(), p.getCompany(), p.getLocation(),
                        p.getJobCategory(), p.getEmploymentType(), p.getExperienceLevel(),
                        p.getApplyUrl(), p.getDeadline(), p.getCreatedAt(),
                        determineMatchType(condition, p.getLocation(), p.getExperienceLevel(), p.getEmploymentType())
                ))
                .toList();
    }

    public long countPrivate(SearchCondition condition) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(p) FROM PrivateJobPosting p WHERE p.isClosed = false"
                + " AND p.jobCategory IS NOT NULL AND p.jobCategory NOT IN :excludedCategories");

        List<String> predicates = buildPrivatePredicates(condition);
        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }

        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
        bindPrivateParams(query, condition);
        return query.getSingleResult();
    }

    /**
     * searchPrivate / countPrivate 공통 WHERE 절 생성.
     *
     * <ul>
     *   <li>경력: 고용형태 null 없음 → strict IN만 사용 (IS NULL 불필요)</li>
     *   <li>고용형태: null 없음 → strict IN (IS NULL 불필요)</li>
     *   <li>지역: null 많음 → LIKE OR IS NULL 유지</li>
     *   <li>exactGroups / semanticGroups: 그룹 간 AND, 그룹 내 terms OR</li>
     * </ul>
     */
    private List<String> buildPrivatePredicates(SearchCondition condition) {
        List<String> predicates = new ArrayList<>();

        if (condition.categories() != null && !condition.categories().isEmpty()) {
            predicates.add("p.jobCategory IN :categories");
        }
        if (hasText(condition.company())) {
            predicates.add("p.company = :company");
        }
        if (hasText(condition.location())) {
            // 지역 null이 많으므로 STRICT에서도 IS NULL 포함
            predicates.add("(LOWER(p.location) LIKE :locationPattern OR p.location IS NULL)");
        }
        if (condition.experienceLevels() != null && !condition.experienceLevels().isEmpty()) {
            // 경력은 null 없고 '미확인' 문자열로 저장됨 → strict IN
            // "신입" 조건: ["신입", "무관", "미확인"] 포함 (deriveExperienceLevels)
            // "경력" 조건: ["경력", "무관"] — UNKNOWN_STRUCTURAL 그룹에서 "미확인" 추가
            predicates.add("p.experienceLevel IN :expLevels");
        }
        if (condition.employmentTypes() != null && !condition.employmentTypes().isEmpty()) {
            // 고용형태는 null/미확인 없음 → strict IN
            predicates.add("p.employmentType IN :empTypes");
        }

        // exactGroups: 각 RequirementGroup → AND 블록, 그룹 내 terms → OR
        List<RequirementGroup> exactGroups = condition.exactGroups() != null ? condition.exactGroups() : List.of();
        for (int g = 0; g < exactGroups.size(); g++) {
            List<String> terms = exactGroups.get(g).terms();
            List<String> orClauses = new ArrayList<>();
            for (int t = 0; t < terms.size(); t++) {
                String param = "exG" + g + "T" + t;
                orClauses.add("LOWER(p.title) LIKE :" + param + " OR LOWER(p.description) LIKE :" + param);
            }
            if (!orClauses.isEmpty()) {
                predicates.add("(" + String.join(" OR ", orClauses) + ")");
            }
        }

        // semanticGroups: 동일 패턴
        List<RequirementGroup> semanticGroups = condition.semanticGroups() != null ? condition.semanticGroups() : List.of();
        for (int g = 0; g < semanticGroups.size(); g++) {
            List<String> terms = semanticGroups.get(g).terms();
            List<String> orClauses = new ArrayList<>();
            for (int t = 0; t < terms.size(); t++) {
                String param = "semG" + g + "T" + t;
                orClauses.add("LOWER(p.title) LIKE :" + param + " OR LOWER(p.description) LIKE :" + param);
            }
            if (!orClauses.isEmpty()) {
                predicates.add("(" + String.join(" OR ", orClauses) + ")");
            }
        }

        return predicates;
    }

    /** searchPrivate / countPrivate 공통 파라미터 바인딩. */
    private void bindPrivateParams(TypedQuery<?> query, SearchCondition condition) {
        query.setParameter("excludedCategories", EXCLUDED_CATEGORIES);
        if (condition.categories() != null && !condition.categories().isEmpty()) {
            query.setParameter("categories", condition.categories());
        }
        if (hasText(condition.company())) {
            query.setParameter("company", condition.company());
        }
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        if (condition.experienceLevels() != null && !condition.experienceLevels().isEmpty()) {
            query.setParameter("expLevels", condition.experienceLevels());
        }
        if (condition.employmentTypes() != null && !condition.employmentTypes().isEmpty()) {
            query.setParameter("empTypes", condition.employmentTypes());
        }

        List<RequirementGroup> exactGroups = condition.exactGroups() != null ? condition.exactGroups() : List.of();
        for (int g = 0; g < exactGroups.size(); g++) {
            List<String> terms = exactGroups.get(g).terms();
            for (int t = 0; t < terms.size(); t++) {
                query.setParameter("exG" + g + "T" + t, "%" + terms.get(t).toLowerCase() + "%");
            }
        }

        List<RequirementGroup> semanticGroups = condition.semanticGroups() != null ? condition.semanticGroups() : List.of();
        for (int g = 0; g < semanticGroups.size(); g++) {
            List<String> terms = semanticGroups.get(g).terms();
            for (int t = 0; t < terms.size(); t++) {
                query.setParameter("semG" + g + "T" + t, "%" + terms.get(t).toLowerCase() + "%");
            }
        }
    }

    public List<JobSummary> searchPublic(SearchCondition condition, int offset, int limit) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM PublicJobPosting p WHERE (p.isClosed IS NULL OR p.isClosed = false)");

        List<String> predicates = buildPublicPredicates(condition);
        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<?> query = em.createQuery(jpql.toString(),
                com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting.class);
        bindPublicParams(query, condition);
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        @SuppressWarnings("unchecked")
        List<com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting> results =
                (List<com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting>) query.getResultList();

        return results.stream()
                .map(p -> JobSummary.of(
                        p.getId(), "PUBLIC", p.getTitle(), p.getCompanyName(), p.getWorkRegion(),
                        null, p.getRecrutType(), null, p.getApplyLink(), p.getEndDate(),
                        p.getCreatedAt(),
                        determinePublicMatchType(condition, p.getWorkRegion(), p.getRecrutType())
                ))
                .toList();
    }

    public long countPublic(SearchCondition condition) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(p) FROM PublicJobPosting p WHERE (p.isClosed IS NULL OR p.isClosed = false)");

        List<String> predicates = buildPublicPredicates(condition);
        for (String pred : predicates) {
            jpql.append(" AND ").append(pred);
        }

        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
        bindPublicParams(query, condition);
        return query.getSingleResult();
    }

    private List<String> buildPublicPredicates(SearchCondition condition) {
        List<String> predicates = new ArrayList<>();

        // PublicJobPosting에는 jobCategory가 없으므로 카테고리 라벨을 title/jobRole LIKE로 검색
        List<String> categoryKeywords = condition.categories() != null
                ? condition.categories().stream()
                    .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList()
                : List.of();

        if (!categoryKeywords.isEmpty()) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < categoryKeywords.size(); i++) {
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
        if (condition.employmentTypes() != null && !condition.employmentTypes().isEmpty()) {
            predicates.add("(p.recrutType IN :empTypes OR p.recrutType IS NULL)");
        }

        return predicates;
    }

    private void bindPublicParams(TypedQuery<?> query, SearchCondition condition) {
        List<String> categoryKeywords = condition.categories() != null
                ? condition.categories().stream()
                    .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList()
                : List.of();

        for (int i = 0; i < categoryKeywords.size(); i++) {
            query.setParameter("pubKw" + i, "%" + categoryKeywords.get(i).toLowerCase() + "%");
        }
        if (hasText(condition.location())) {
            query.setParameter("locationPattern", "%" + condition.location().trim().toLowerCase() + "%");
        }
        if (hasText(condition.experience())) {
            query.setParameter("experiencePattern", "%" + condition.experience().trim().toLowerCase() + "%");
        }
        if (condition.employmentTypes() != null && !condition.employmentTypes().isEmpty()) {
            query.setParameter("empTypes", condition.employmentTypes());
        }
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
                || "미확인".equals(actualExpLevel)  // 경력 미확인 공고 → SIMILAR 배지
                || !condition.experienceLevels().contains(actualExpLevel))) {
            return "SIMILAR";
        }
        boolean hasEmpFilter = condition.employmentTypes() != null && !condition.employmentTypes().isEmpty();
        if (hasEmpFilter && (actualEmpType == null || !condition.employmentTypes().contains(actualEmpType))) {
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

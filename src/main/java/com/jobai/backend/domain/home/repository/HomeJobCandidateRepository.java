package com.jobai.backend.domain.home.repository;

import com.jobai.backend.domain.home.constant.EmploymentTypeAlias;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.jobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 홈 화면 맞춤 공고 추천용 후보 조회 리포지토리.
 * EntityManager 기반 동적 JPQL 구성 방식은 JobSearchRepository 패턴을 그대로 따른다.
 */
@Repository
@RequiredArgsConstructor
public class HomeJobCandidateRepository {

    private final EntityManager em;

    private static final List<String> EXCLUDED_CATEGORIES = List.of("미분류", "비대상");

    private record PredicateResult(List<String> predicates, Map<String, String> params) {
    }

    // ─────────────────────────── PUBLIC(공기업) ───────────────────────────

    public List<JobCandidate> findPublicCandidates(List<String> locations, List<String> employmentTypes, int limit) {
        PredicateResult pr = buildPublicPredicates(locations, employmentTypes);

        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM PublicJobPosting p WHERE (p.isClosed IS NULL OR p.isClosed = false)");
        for (String pred : pr.predicates()) {
            jpql.append(" AND ").append(pred);
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<PublicJobPosting> query = em.createQuery(jpql.toString(), PublicJobPosting.class);
        pr.params().forEach(query::setParameter);
        query.setMaxResults(limit);

        return query.getResultList().stream()
                .map(p -> new JobCandidate(
                        p.getId(),
                        "PUBLIC",
                        p.getCompanyName(),
                        p.getTitle(),
                        p.getWorkRegion(),
                        p.getRecrutType(),
                        p.getJobRole(),
                        p.getEndDate(),
                        p.getCreatedAt()
                ))
                .toList();
    }

    /** id 목록으로 특정 공고들을 그대로 조회한다(마감 여부와 무관 — 스크랩 목록처럼 "이미 알고 있는 id"를 다시 보여줄 때 사용). */
    public List<JobCandidate> findPublicCandidatesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        TypedQuery<PublicJobPosting> query = em.createQuery(
                "SELECT p FROM PublicJobPosting p WHERE p.id IN :ids", PublicJobPosting.class);
        query.setParameter("ids", ids);

        return query.getResultList().stream()
                .map(p -> new JobCandidate(
                        p.getId(),
                        "PUBLIC",
                        p.getCompanyName(),
                        p.getTitle(),
                        p.getWorkRegion(),
                        p.getRecrutType(),
                        p.getJobRole(),
                        p.getEndDate(),
                        p.getCreatedAt()
                ))
                .toList();
    }

    private PredicateResult buildPublicPredicates(List<String> locations, List<String> employmentTypes) {
        List<String> predicates = new ArrayList<>();
        Map<String, String> params = new LinkedHashMap<>();

        if (hasItems(locations)) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < locations.size(); i++) {
                String key = "loc" + i;
                orClauses.add("LOWER(p.workRegion) LIKE :" + key);
                params.put(key, likePattern(locations.get(i)));
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        List<String> recrutKeywords = new ArrayList<>();
        List<String> experienceKeywords = new ArrayList<>();
        if (hasItems(employmentTypes)) {
            for (String type : employmentTypes) {
                List<String> recrutKw = EmploymentTypeAlias.PUBLIC_RECRUT_TYPE_KEYWORDS.get(type);
                if (recrutKw != null) {
                    recrutKeywords.addAll(recrutKw);
                }
                List<String> expKw = EmploymentTypeAlias.PUBLIC_WORK_EXPERIENCE_KEYWORDS.get(type);
                if (expKw != null) {
                    experienceKeywords.addAll(expKw);
                }
            }
        }
        if (!recrutKeywords.isEmpty() || !experienceKeywords.isEmpty()) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < recrutKeywords.size(); i++) {
                String key = "recrutKw" + i;
                orClauses.add("LOWER(p.recrutType) LIKE :" + key);
                params.put(key, likePattern(recrutKeywords.get(i)));
            }
            for (int i = 0; i < experienceKeywords.size(); i++) {
                String key = "expKw" + i;
                orClauses.add("LOWER(p.workExperience) LIKE :" + key);
                params.put(key, likePattern(experienceKeywords.get(i)));
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        return new PredicateResult(predicates, params);
    }

    // ─────────────────────────── PRIVATE(사기업) ───────────────────────────

    public List<JobCandidate> findPrivateCandidates(List<String> locations, List<String> employmentTypes, int limit) {
        PredicateResult pr = buildPrivatePredicates(locations, employmentTypes);

        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM PrivateJobPosting p WHERE p.isClosed = false"
                + " AND p.jobCategory IS NOT NULL AND p.jobCategory NOT IN :excludedCategories");
        for (String pred : pr.predicates()) {
            jpql.append(" AND ").append(pred);
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<PrivateJobPosting> query = em.createQuery(jpql.toString(), PrivateJobPosting.class);
        query.setParameter("excludedCategories", EXCLUDED_CATEGORIES);
        pr.params().forEach(query::setParameter);
        query.setMaxResults(limit);

        return query.getResultList().stream()
                .map(p -> new JobCandidate(
                        p.getId(),
                        "PRIVATE",
                        p.getCompany(),
                        p.getTitle(),
                        p.getLocation(),
                        p.getEmploymentType(),
                        p.getJobCategory(),
                        p.getDeadline(),
                        p.getCreatedAt()
                ))
                .toList();
    }

    /** id 목록으로 특정 공고들을 그대로 조회한다(마감 여부와 무관 — 스크랩 목록처럼 "이미 알고 있는 id"를 다시 보여줄 때 사용). */
    public List<JobCandidate> findPrivateCandidatesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        TypedQuery<PrivateJobPosting> query = em.createQuery(
                "SELECT p FROM PrivateJobPosting p WHERE p.id IN :ids", PrivateJobPosting.class);
        query.setParameter("ids", ids);

        return query.getResultList().stream()
                .map(p -> new JobCandidate(
                        p.getId(),
                        "PRIVATE",
                        p.getCompany(),
                        p.getTitle(),
                        p.getLocation(),
                        p.getEmploymentType(),
                        p.getJobCategory(),
                        p.getDeadline(),
                        p.getCreatedAt()
                ))
                .toList();
    }

    private PredicateResult buildPrivatePredicates(List<String> locations, List<String> employmentTypes) {
        List<String> predicates = new ArrayList<>();
        Map<String, String> params = new LinkedHashMap<>();

        if (hasItems(locations)) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < locations.size(); i++) {
                String key = "loc" + i;
                orClauses.add("LOWER(p.location) LIKE :" + key);
                params.put(key, likePattern(locations.get(i)));
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        List<String> keywords = new ArrayList<>();
        if (hasItems(employmentTypes)) {
            for (String type : employmentTypes) {
                List<String> kw = EmploymentTypeAlias.PRIVATE_EMPLOYMENT_TYPE_KEYWORDS.get(type);
                if (kw != null) {
                    keywords.addAll(kw);
                }
            }
        }
        if (!keywords.isEmpty()) {
            List<String> orClauses = new ArrayList<>();
            for (int i = 0; i < keywords.size(); i++) {
                String key = "empKw" + i;
                orClauses.add("LOWER(p.employmentType) LIKE :" + key);
                params.put(key, likePattern(keywords.get(i)));
            }
            predicates.add("(" + String.join(" OR ", orClauses) + ")");
        }

        return new PredicateResult(predicates, params);
    }

    private static boolean hasItems(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private static String likePattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}

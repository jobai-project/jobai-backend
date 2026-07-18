package com.jobai.backend.domain.home.repository;

import com.jobai.backend.domain.home.constant.EmploymentTypeAlias;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.global.enums.JobCategory;
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

    private record PredicateResult(List<String> predicates, Map<String, String> params) {
    }

    public record ScoredJobCandidate(JobCandidate candidate, Integer score) {
    }

    public List<JobCandidate> findLatestPublicCandidates(
            List<String> locations,
            List<String> employmentTypes,
            List<String> preferredJobCategories,
            List<String> preferredLocations,
            int limit
    ) {
        PredicateResult pr = addPreferencePredicates(
                buildPublicPredicates(locations, employmentTypes),
                "p.jobRole", "p.workRegion", preferredJobCategories, preferredLocations
        );
        String jpql = buildQuery(
                "SELECT p FROM PublicJobPosting p",
                "(p.isClosed IS NULL OR p.isClosed = false)",
                pr,
                "p.createdAt DESC"
        );
        TypedQuery<PublicJobPosting> query = em.createQuery(jpql, PublicJobPosting.class)
                .setMaxResults(limit);
        pr.params().forEach(query::setParameter);
        return query.getResultList().stream().map(this::toPublicCandidate).toList();
    }

    public long countLatestPublicCandidates(
            List<String> locations,
            List<String> employmentTypes,
            List<String> preferredJobCategories,
            List<String> preferredLocations
    ) {
        PredicateResult pr = addPreferencePredicates(
                buildPublicPredicates(locations, employmentTypes),
                "p.jobRole", "p.workRegion", preferredJobCategories, preferredLocations
        );
        String jpql = buildQuery(
                "SELECT COUNT(p) FROM PublicJobPosting p",
                "(p.isClosed IS NULL OR p.isClosed = false)",
                pr,
                null
        );
        TypedQuery<Long> query = em.createQuery(jpql, Long.class);
        pr.params().forEach(query::setParameter);
        return query.getSingleResult();
    }

    public List<JobCandidate> findLatestPrivateCandidates(
            List<String> locations,
            List<String> employmentTypes,
            List<String> preferredJobCategories,
            List<String> preferredLocations,
            int limit
    ) {
        PredicateResult pr = addPreferencePredicates(
                buildPrivatePredicates(locations, employmentTypes),
                "p.jobCategory", "p.location", preferredJobCategories, preferredLocations
        );
        String jpql = buildQuery(
                "SELECT p FROM PrivateJobPosting p",
                "p.isClosed = false AND p.jobCategory IN :validCategories",
                pr,
                "p.createdAt DESC"
        );
        TypedQuery<PrivateJobPosting> query = em.createQuery(jpql, PrivateJobPosting.class)
                .setParameter("validCategories", JobCategory.matchTargetLabels())
                .setMaxResults(limit);
        pr.params().forEach(query::setParameter);
        return query.getResultList().stream().map(this::toPrivateCandidate).toList();
    }

    public long countLatestPrivateCandidates(
            List<String> locations,
            List<String> employmentTypes,
            List<String> preferredJobCategories,
            List<String> preferredLocations
    ) {
        PredicateResult pr = addPreferencePredicates(
                buildPrivatePredicates(locations, employmentTypes),
                "p.jobCategory", "p.location", preferredJobCategories, preferredLocations
        );
        String jpql = buildQuery(
                "SELECT COUNT(p) FROM PrivateJobPosting p",
                "p.isClosed = false AND p.jobCategory IN :validCategories",
                pr,
                null
        );
        TypedQuery<Long> query = em.createQuery(jpql, Long.class)
                .setParameter("validCategories", JobCategory.matchTargetLabels());
        pr.params().forEach(query::setParameter);
        return query.getSingleResult();
    }

    public List<ScoredJobCandidate> findScoredPublicCandidates(
            Long resumeId,
            List<String> locations,
            List<String> employmentTypes,
            int threshold,
            int limit
    ) {
        PredicateResult pr = buildPublicPredicates(locations, employmentTypes);
        String jpql = buildQuery(
                "SELECT p, s.score FROM PublicMatchScore s JOIN s.publicJobPosting p",
                "s.resume.id = :resumeId AND s.score >= :threshold"
                        + " AND (p.isClosed IS NULL OR p.isClosed = false)",
                pr,
                "s.score DESC, p.createdAt DESC"
        );

        var query = em.createQuery(jpql, Object[].class)
                .setParameter("resumeId", resumeId)
                .setParameter("threshold", threshold)
                .setMaxResults(limit);
        pr.params().forEach(query::setParameter);

        return query.getResultList().stream()
                .map(row -> new ScoredJobCandidate(toPublicCandidate((PublicJobPosting) row[0]), (Integer) row[1]))
                .toList();
    }

    public long countScoredPublicCandidates(
            Long resumeId, List<String> locations, List<String> employmentTypes, int threshold
    ) {
        PredicateResult pr = buildPublicPredicates(locations, employmentTypes);
        String jpql = buildQuery(
                "SELECT COUNT(s) FROM PublicMatchScore s JOIN s.publicJobPosting p",
                "s.resume.id = :resumeId AND s.score >= :threshold"
                        + " AND (p.isClosed IS NULL OR p.isClosed = false)",
                pr,
                null
        );
        TypedQuery<Long> query = em.createQuery(jpql, Long.class)
                .setParameter("resumeId", resumeId)
                .setParameter("threshold", threshold);
        pr.params().forEach(query::setParameter);
        return query.getSingleResult();
    }

    public List<ScoredJobCandidate> findScoredPrivateCandidates(
            Long resumeId,
            List<String> locations,
            List<String> employmentTypes,
            int threshold,
            int limit
    ) {
        PredicateResult pr = buildPrivatePredicates(locations, employmentTypes);
        String jpql = buildQuery(
                "SELECT p, s.score FROM PrivateMatchScore s JOIN s.privateJobPosting p",
                "s.resume.id = :resumeId AND s.score >= :threshold"
                        + " AND p.isClosed = false AND p.jobCategory IN :validCategories",
                pr,
                "s.score DESC, p.createdAt DESC"
        );

        var query = em.createQuery(jpql, Object[].class)
                .setParameter("resumeId", resumeId)
                .setParameter("threshold", threshold)
                .setParameter("validCategories", JobCategory.matchTargetLabels())
                .setMaxResults(limit);
        pr.params().forEach(query::setParameter);

        return query.getResultList().stream()
                .map(row -> new ScoredJobCandidate(toPrivateCandidate((PrivateJobPosting) row[0]), (Integer) row[1]))
                .toList();
    }

    public long countScoredPrivateCandidates(
            Long resumeId, List<String> locations, List<String> employmentTypes, int threshold
    ) {
        PredicateResult pr = buildPrivatePredicates(locations, employmentTypes);
        String jpql = buildQuery(
                "SELECT COUNT(s) FROM PrivateMatchScore s JOIN s.privateJobPosting p",
                "s.resume.id = :resumeId AND s.score >= :threshold"
                        + " AND p.isClosed = false AND p.jobCategory IN :validCategories",
                pr,
                null
        );
        TypedQuery<Long> query = em.createQuery(jpql, Long.class)
                .setParameter("resumeId", resumeId)
                .setParameter("threshold", threshold)
                .setParameter("validCategories", JobCategory.matchTargetLabels());
        pr.params().forEach(query::setParameter);
        return query.getSingleResult();
    }

    private String buildQuery(
            String selectAndFrom, String basePredicate, PredicateResult pr, String orderBy
    ) {
        StringBuilder jpql = new StringBuilder(selectAndFrom)
                .append(" WHERE ")
                .append(basePredicate);
        for (String predicate : pr.predicates()) {
            jpql.append(" AND ").append(predicate);
        }
        if (orderBy != null) {
            jpql.append(" ORDER BY ").append(orderBy);
        }
        return jpql.toString();
    }

    private PredicateResult addPreferencePredicates(
            PredicateResult base,
            String jobCategoryField,
            String locationField,
            List<String> preferredJobCategories,
            List<String> preferredLocations
    ) {
        List<String> predicates = new ArrayList<>(base.predicates());
        Map<String, String> params = new LinkedHashMap<>(base.params());
        addLikeAnyPredicate(predicates, params, jobCategoryField, "prefJob", preferredJobCategories);
        addLikeAnyPredicate(predicates, params, locationField, "prefLocation", preferredLocations);
        return new PredicateResult(predicates, params);
    }

    private void addLikeAnyPredicate(
            List<String> predicates,
            Map<String, String> params,
            String field,
            String parameterPrefix,
            List<String> values
    ) {
        if (!hasItems(values)) {
            return;
        }
        List<String> clauses = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String key = parameterPrefix + i;
            clauses.add("LOWER(" + field + ") LIKE :" + key);
            params.put(key, likePattern(values.get(i)));
        }
        predicates.add("(" + String.join(" OR ", clauses) + ")");
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
                + " AND p.jobCategory IN :validCategories");
        for (String pred : pr.predicates()) {
            jpql.append(" AND ").append(pred);
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<PrivateJobPosting> query = em.createQuery(jpql.toString(), PrivateJobPosting.class);
        query.setParameter("validCategories", JobCategory.matchTargetLabels());
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

    private JobCandidate toPublicCandidate(PublicJobPosting posting) {
        return new JobCandidate(
                posting.getId(),
                "PUBLIC",
                posting.getCompanyName(),
                posting.getTitle(),
                posting.getWorkRegion(),
                posting.getRecrutType(),
                posting.getJobRole(),
                posting.getEndDate(),
                posting.getCreatedAt()
        );
    }

    private JobCandidate toPrivateCandidate(PrivateJobPosting posting) {
        return new JobCandidate(
                posting.getId(),
                "PRIVATE",
                posting.getCompany(),
                posting.getTitle(),
                posting.getLocation(),
                posting.getEmploymentType(),
                posting.getJobCategory(),
                posting.getDeadline(),
                posting.getCreatedAt()
        );
    }

    private static boolean hasItems(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private static String likePattern(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}

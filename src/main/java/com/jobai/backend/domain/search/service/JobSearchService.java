package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.entity.PublicMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.search.dto.MatchLevel;
import com.jobai.backend.domain.search.dto.QueryExpansionResult;
import com.jobai.backend.domain.search.dto.RequirementGroup;
import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.dto.JobSearchResponse.SearchInfo;
import com.jobai.backend.domain.search.dto.SearchCondition;
import com.jobai.backend.domain.search.repository.JobSearchRepository;
import com.jobai.backend.domain.search.repository.VectorSearchRepository;
import com.jobai.backend.domain.search.repository.VectorSearchRepository.ScoredJob;
import com.jobai.backend.domain.search.service.KeywordMatcher.MatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 채용 공고 검색 서비스.
 *
 * <h3>라우팅 전략 (우선순위 순)</h3>
 * <pre>
 * A. unmatchedTokens 없음
 *    → 키워드 검색 (완전 구조화 쿼리)
 *
 * D. exactRequired 있음 + 구조 조건 없음 + semanticRequired 없음
 *    → Exact-first: exactRequired 필터 후 벡터 보충
 *
 * B. 구조 조건 또는 exactRequired 또는 semanticRequired 있음
 *    → 필터 후 그룹 인식 벡터 재정렬 (progressive relaxation)
 *
 * C. 그 외 (preferred only / 순수 자연어)
 *    → 순수 벡터 검색
 * </pre>
 *
 * <h3>Path B 후보 그룹 완화 순서</h3>
 * <pre>
 * 1. STRICT:            모든 조건 (구조 + exactRequired + semanticRequired)
 * 2. UNKNOWN_STRUCTURAL: 경력='미확인' 공고를 추가 포함 (경력 필터 있을 때만)
 * 3. RELAXED_SEMANTIC:  semanticRequired 제거 (exactRequired 유지)
 * 4. RELAXED_EXACT:     exactRequired까지 제거 (구조 조건만)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobSearchService {

    private final KeywordMatcher keywordMatcher;
    private final JobSearchRepository jobSearchRepository;
    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;
    private final QueryExpander queryExpander;
    private final HybridSearchMerger hybridSearchMerger;
    private final SearchReranker searchReranker;

    @Value("${search.embedding.enabled:true}")
    private boolean embeddingEnabled;

    @Value("${search.hybrid.enabled:false}")
    private boolean hybridEnabled;

    /** 벡터 검색 후보 수집 깊이. 페이지 요구량이 이보다 크면 확장된다. */
    private static final int HYBRID_CANDIDATE_DEPTH = 200;

    private static final double VECTOR_THRESHOLD = 0.4;

    /** 이 수보다 후보가 적으면 다음 완화 그룹을 생성한다. */
    private static final int MIN_CANDIDATES = 20;

    private static final Map<String, Integer> MATCH_TYPE_ORDER = Map.of(
            "EXACT", 0, "SIMILAR", 1);

    private static final Map<String, Integer> MATCH_LEVEL_ORDER = Map.of(
            MatchLevel.STRICT.name(), 0,
            MatchLevel.UNKNOWN_STRUCTURAL.name(), 1,
            MatchLevel.RELAXED_SEMANTIC.name(), 2,
            MatchLevel.RELAXED_EXACT.name(), 3);

    /** matchLevel과 해당 그룹의 공고 목록을 묶는 내부 레코드. */
    private record CandidateGroup(MatchLevel matchLevel, List<JobSummary> jobs) {}

    // ═══════════════════════════════ 진입점 ═══════════════════════════════

    public JobSearchResponse search(String query, int page, int size, String email) {
        MatchResult match = keywordMatcher.extract(query);
        log.info("[검색] query={}, categories={}, company={}, location={}, experience={}, unmatched={}",
                query, match.categories(), match.company(), match.location(),
                match.experience(), match.unmatchedTokens());

        QueryExpansionResult expansion = queryExpander.expand(query, match.unmatchedTokens());

        // QueryExpander가 비활성이거나 required 분류 결과가 없을 때
        // unmatchedTokens를 exactRequired로 변환해 title·description LIKE 필터를 보장한다.
        // 예: "커머스 주니어" → "커머스"가 unmatched → exactRequired[커머스]: 커머스
        if (!expansion.wasExpanded() && match.hasUnmatchedTokens()) {
            List<RequirementGroup> titleFallback = match.unmatchedTokens().stream()
                    .map(token -> new RequirementGroup(token.toUpperCase(), List.of(token)))
                    .toList();
            expansion = new QueryExpansionResult(
                    expansion.expandedText(),
                    expansion.expandedKeywords(),
                    titleFallback,
                    expansion.exactPreferred(),
                    expansion.semanticRequired(),
                    expansion.semanticPreferred()
            );
        }

        boolean hasStructuralFilters = !match.categories().isEmpty()
                || match.experience() != null
                || match.company() != null
                || match.location() != null;

        int requiredForPage = page * size + size;
        int targetSize = Math.max(HYBRID_CANDIDATE_DEPTH, requiredForPage);

        JobSearchResponse response;

        if (!match.hasUnmatchedTokens()) {
            // Path A: 완전 구조화 쿼리 → 키워드 검색
            log.info("[PathA] 키워드 검색: query={}", query);
            SearchCondition cond = buildCondition(match, SearchCondition.METHOD_KEYWORD, List.of(), List.of());
            response = executeKeywordSearch(cond);

        } else if (hybridEnabled && embeddingEnabled
                && !expansion.exactRequired().isEmpty()
                && !hasStructuralFilters
                && expansion.semanticRequired().isEmpty()) {
            // Path D: exactRequired만 있고 구조 조건·semanticRequired 없음
            log.info("[PathD] Exact-first: query={}, exactRequired={}", query, expansion.exactRequired());
            response = executeExactFirstSearch(query, expansion, match, targetSize);

        } else if (hybridEnabled && embeddingEnabled
                && (hasStructuralFilters
                    || !expansion.exactRequired().isEmpty()
                    || !expansion.semanticRequired().isEmpty())) {
            // Path B: 구조 조건 또는 exactRequired 또는 semanticRequired 있음
            log.info("[PathB] 필터+벡터 재정렬: query={}, structural={}, exact={}, semantic={}",
                    query, hasStructuralFilters, expansion.exactRequired(), expansion.semanticRequired());
            response = executeFilteredVectorSearch(query, expansion, match, targetSize);

        } else if (embeddingEnabled) {
            // Path C: 순수 자연어 (preferred only)
            log.info("[PathC] 순수 벡터: query={}", query);
            response = executePureVectorSearch(query, expansion, match, targetSize);

        } else {
            // 임베딩 비활성 → 키워드 폴백
            SearchCondition fallback = buildCondition(match, SearchCondition.METHOD_KEYWORD, List.of(), List.of());
            response = executeKeywordSearch(fallback);
        }

        int offset = page * size;
        List<JobSummary> pagedJobs = response.jobs().stream().skip(offset).limit(size).toList();

        SearchInfo finalInfo = new SearchInfo(
                response.searchInfo().method(),
                response.searchInfo().matchedCategories(),
                expansion.expandedKeywords());

        return new JobSearchResponse(response.totalCount(), attachMatchScores(pagedJobs, email), finalInfo);
    }

    // ═══════════════════════════════ Path A: 키워드 검색 ═══════════════════════════════

    private JobSearchResponse executeKeywordSearch(SearchCondition condition) {
        List<JobSummary> privateResults = "PUBLIC".equals(condition.sourceType()) ? List.of()
                : jobSearchRepository.searchPrivate(condition, 0, HYBRID_CANDIDATE_DEPTH);
        // company 필터가 있고 공공기관 검색이 아니면 공기업 공고 제외
        // (공공기관 검색 시 sourceType=PUBLIC이 자동 설정되므로 정상 통과)
        boolean skipPublic = "PRIVATE".equals(condition.sourceType())
                || (condition.company() != null && !condition.company().isBlank()
                    && !"PUBLIC".equals(condition.sourceType()));
        List<JobSummary> publicResults = skipPublic ? List.of()
                : jobSearchRepository.searchPublic(condition, 0, HYBRID_CANDIDATE_DEPTH);

        List<JobSummary> allResults = Stream.concat(privateResults.stream(), publicResults.stream())
                .sorted(Comparator
                        .comparingInt((JobSummary j) -> MATCH_TYPE_ORDER.getOrDefault(j.matchType(), 3))
                        .thenComparing(JobSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long totalCount = ("PUBLIC".equals(condition.sourceType()) ? 0 : jobSearchRepository.countPrivate(condition))
                + (skipPublic ? 0 : jobSearchRepository.countPublic(condition));

        return new JobSearchResponse(totalCount, allResults,
                new SearchInfo(condition.method(), condition.categories(), List.of()));
    }

    // ═══════════════════════════════ Path B: 필터 후 벡터 재정렬 ═══════════════════════════════

    /**
     * 구조 조건 + exactRequired + semanticRequired를 모두 적용한 STRICT 후보를 수집하고,
     * 후보가 targetSize에 미치지 못하면 완화 그룹을 순서대로 추가한다.
     * 각 그룹을 독립적으로 벡터 재정렬하여 primary 그룹이 항상 supplement보다 앞에 위치한다.
     */
    private JobSearchResponse executeFilteredVectorSearch(String query, QueryExpansionResult expansion,
                                                           MatchResult match, int targetSize) {
        boolean hasExpFilter = match.experienceLevels() != null && !match.experienceLevels().isEmpty();
        boolean hasSemantic = !expansion.semanticRequired().isEmpty();
        boolean hasExact = !expansion.exactRequired().isEmpty();

        List<CandidateGroup> groups = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        // ── 그룹 1: STRICT ─────────────────────────────────────────────────
        SearchCondition strictCond = buildCondition(match, SearchCondition.METHOD_HYBRID,
                expansion.exactRequired(), expansion.semanticRequired());
        List<JobSummary> strictJobs = fetchPrivateAndPublic(strictCond, targetSize);
        if (!strictJobs.isEmpty()) {
            groups.add(new CandidateGroup(MatchLevel.STRICT, strictJobs));
            strictJobs.forEach(j -> seenIds.add(uniqueId(j)));
        }

        // ── 그룹 2: UNKNOWN_STRUCTURAL ──────────────────────────────────────
        // 경력='미확인' 공고 추가 포함 (경력 필터 있을 때만)
        if (hasExpFilter && totalCandidates(groups) < targetSize) {
            SearchCondition unknownCond = buildUnknownStructuralCondition(
                    match, expansion.exactRequired(), expansion.semanticRequired());
            List<JobSummary> unknownJobs = fetchPrivateAndPublic(unknownCond, targetSize)
                    .stream().filter(j -> !seenIds.contains(uniqueId(j))).toList();
            if (!unknownJobs.isEmpty()) {
                groups.add(new CandidateGroup(MatchLevel.UNKNOWN_STRUCTURAL, unknownJobs));
                unknownJobs.forEach(j -> seenIds.add(uniqueId(j)));
            }
        }

        // ── 그룹 3: RELAXED_SEMANTIC ────────────────────────────────────────
        // semanticRequired 제거 (exactRequired 유지)
        if (hasSemantic && totalCandidates(groups) < targetSize) {
            SearchCondition relaxedSemCond = buildCondition(match, SearchCondition.METHOD_HYBRID,
                    expansion.exactRequired(), List.of());
            List<JobSummary> relaxedSemJobs = fetchPrivateAndPublic(relaxedSemCond, targetSize)
                    .stream().filter(j -> !seenIds.contains(uniqueId(j))).toList();
            if (!relaxedSemJobs.isEmpty()) {
                groups.add(new CandidateGroup(MatchLevel.RELAXED_SEMANTIC, relaxedSemJobs));
                relaxedSemJobs.forEach(j -> seenIds.add(uniqueId(j)));
            }
        }

        // ── 그룹 4: RELAXED_EXACT ───────────────────────────────────────────
        // exactRequired까지 제거 (구조 조건만 유지)
        if (hasExact && totalCandidates(groups) < targetSize) {
            SearchCondition relaxedExactCond = buildCondition(match, SearchCondition.METHOD_HYBRID,
                    List.of(), List.of());
            List<JobSummary> relaxedExactJobs = fetchPrivateAndPublic(relaxedExactCond, targetSize)
                    .stream().filter(j -> !seenIds.contains(uniqueId(j))).toList();
            if (!relaxedExactJobs.isEmpty()) {
                groups.add(new CandidateGroup(MatchLevel.RELAXED_EXACT, relaxedExactJobs));
            }
        }

        long totalCount = totalCandidates(groups);
        float[] queryVector = tryEmbed(expansion.expandedText(), "[PathB]");
        List<JobSummary> allResults = rerankGroups(query, queryVector, groups);

        log.info("[PathB] 완료: groups={}, total={}, query={}",
                groups.stream().map(g -> g.matchLevel() + ":" + g.jobs().size()).toList(),
                totalCount, query);

        return new JobSearchResponse(totalCount, allResults,
                new SearchInfo(SearchCondition.METHOD_HYBRID, match.categories(), expansion.expandedKeywords()));
    }

    // ═══════════════════════════════ Path D: Exact-first ═══════════════════════════════

    /**
     * exactRequired 필터로 STRICT 후보를 수집하고, 부족하면 벡터 검색으로 RELAXED_EXACT 보충.
     * 구조 조건이 없는 exactRequired 쿼리 전용 ("Redis 사용하는 곳" 등).
     */
    private JobSearchResponse executeExactFirstSearch(String query, QueryExpansionResult expansion,
                                                       MatchResult match, int targetSize) {
        List<CandidateGroup> groups = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        // ── 그룹 1: STRICT (exactRequired 필터) ────────────────────────────
        SearchCondition exactCond = buildCondition(match, SearchCondition.METHOD_HYBRID,
                expansion.exactRequired(), List.of());
        List<JobSummary> exactJobs = fetchPrivateAndPublic(exactCond, targetSize);
        if (!exactJobs.isEmpty()) {
            groups.add(new CandidateGroup(MatchLevel.STRICT, exactJobs));
            exactJobs.forEach(j -> seenIds.add(uniqueId(j)));
        }

        // 임베딩 한 번만 수행 (벡터 보충 + 재정렬 공유)
        float[] queryVector = tryEmbed(expansion.expandedText(), "[PathD]");

        // ── 그룹 2: RELAXED_EXACT (벡터 보충) ──────────────────────────────
        // exactRequired 조건을 만족하지 않지만 의미적으로 유사한 공고 보충
        if (queryVector != null && totalCandidates(groups) < targetSize) {
            SearchCondition vectorCond = buildCondition(match, SearchCondition.METHOD_VECTOR,
                    List.of(), List.of());
            try {
                List<JobSummary> vectorJobs = Stream.concat(
                        "PUBLIC".equals(vectorCond.sourceType()) ? Stream.empty()
                                : vectorSearchRepository.searchPrivateByVector(
                                        queryVector, VECTOR_THRESHOLD, vectorCond, 0, targetSize)
                                        .stream().map(ScoredJob::job),
                        "PRIVATE".equals(vectorCond.sourceType()) ? Stream.empty()
                                : vectorSearchRepository.searchPublicByVector(
                                        queryVector, VECTOR_THRESHOLD, vectorCond, 0, targetSize)
                                        .stream().map(ScoredJob::job)
                ).filter(j -> !seenIds.contains(uniqueId(j))).toList();

                if (!vectorJobs.isEmpty()) {
                    groups.add(new CandidateGroup(MatchLevel.RELAXED_EXACT, vectorJobs));
                }
            } catch (Exception e) {
                log.warn("[PathD] 벡터 보충 실패: query={}, error={}", query, e.getMessage());
            }
        }

        long totalCount = totalCandidates(groups);
        List<JobSummary> allResults = rerankGroups(query, queryVector, groups);

        log.info("[PathD] 완료: groups={}, total={}, query={}",
                groups.stream().map(g -> g.matchLevel() + ":" + g.jobs().size()).toList(),
                totalCount, query);

        return new JobSearchResponse(totalCount, allResults,
                new SearchInfo(SearchCondition.METHOD_HYBRID, match.categories(), expansion.expandedKeywords()));
    }

    // ═══════════════════════════════ Path C: 순수 벡터 검색 ═══════════════════════════════

    private JobSearchResponse executePureVectorSearch(String query, QueryExpansionResult expansion,
                                                       MatchResult match, int targetSize) {
        SearchCondition cond = buildCondition(match, SearchCondition.METHOD_VECTOR, List.of(), List.of());
        try {
            float[] queryVector = embeddingService.embedQuery(expansion.expandedText());

            List<JobSummary> results = Stream.concat(
                    "PUBLIC".equals(cond.sourceType()) ? Stream.empty()
                            : vectorSearchRepository.searchPrivateByVector(
                                    queryVector, VECTOR_THRESHOLD, cond, 0, targetSize).stream(),
                    "PRIVATE".equals(cond.sourceType()) ? Stream.empty()
                            : vectorSearchRepository.searchPublicByVector(
                                    queryVector, VECTOR_THRESHOLD, cond, 0, targetSize).stream()
            ).sorted(Comparator.comparingDouble(ScoredJob::distance))
                    .map(ScoredJob::job)
                    .toList();

            long totalCount = ("PUBLIC".equals(cond.sourceType()) ? 0
                            : vectorSearchRepository.countPrivateByVector(queryVector, VECTOR_THRESHOLD, cond))
                    + ("PRIVATE".equals(cond.sourceType()) ? 0
                            : vectorSearchRepository.countPublicByVector(queryVector, VECTOR_THRESHOLD, cond));

            // Path C는 단일 그룹이므로 전체에 rerank 적용
            return new JobSearchResponse(totalCount, searchReranker.rerank(query, results),
                    new SearchInfo(SearchCondition.METHOD_VECTOR, match.categories(), expansion.expandedKeywords()));

        } catch (Exception e) {
            log.warn("[PathC] 벡터 검색 실패, 키워드 폴백: query={}", query, e);
            return executeKeywordSearch(
                    buildCondition(match, SearchCondition.METHOD_KEYWORD, List.of(), List.of()));
        }
    }

    // ═══════════════════════════════ 그룹 인식 벡터 재정렬 ═══════════════════════════════

    /**
     * 각 CandidateGroup을 독립적으로 벡터 재정렬한다.
     *
     * <ul>
     *   <li>그룹 순서 보장: 앞 그룹 결과가 항상 뒷 그룹보다 먼저 노출</li>
     *   <li>queryVector가 null이거나 재정렬 실패 시 matchLevel 순 → createdAt 역순 폴백</li>
     * </ul>
     */
    private List<JobSummary> rerankGroups(String query, float[] queryVector, List<CandidateGroup> groups) {
        if (groups.isEmpty()) return List.of();
        if (queryVector == null) return fallbackSort(groups);

        try {
            List<JobSummary> result = new ArrayList<>();
            for (CandidateGroup group : groups) {
                // rankByVector는 matchType을 "EXACT"로 하드코딩하므로 원본 배지를 미리 보존
                Map<String, String> originalMatchTypes = group.jobs().stream()
                        .collect(Collectors.toMap(
                                JobSearchService::uniqueId, JobSummary::matchType, (a, b) -> a));

                List<Long> privateIds = group.jobs().stream()
                        .filter(j -> "PRIVATE".equals(j.source())).map(JobSummary::id).toList();
                List<Long> publicIds = group.jobs().stream()
                        .filter(j -> "PUBLIC".equals(j.source())).map(JobSummary::id).toList();

                List<JobSummary> rankedGroup = Stream.concat(
                        privateIds.isEmpty() ? Stream.empty()
                                : vectorSearchRepository.rankPrivateByVector(queryVector, privateIds).stream(),
                        publicIds.isEmpty() ? Stream.empty()
                                : vectorSearchRepository.rankPublicByVector(queryVector, publicIds).stream()
                ).sorted(Comparator.comparingDouble(ScoredJob::distance))
                        .map(ScoredJob::job)
                        .map(j -> j.withMatchLevel(group.matchLevel().name()))
                        .map(j -> {
                            // 원본 matchType 복원 (예: 미확인 공고의 SIMILAR 배지 유지)
                            String orig = originalMatchTypes.get(uniqueId(j));
                            return orig != null ? j.withMatchType(orig) : j;
                        })
                        .toList();

                // 그룹 내부에서만 rerank 적용 → 그룹 순서(STRICT > RELAXED) 보장
                // rerank 후 EXACT → SIMILAR 순 정렬: '미확인' 공고가 '신입'/'무관' 앞에 끼지 않도록
                List<JobSummary> reranked = searchReranker.rerank(query, rankedGroup);
                List<JobSummary> sortedByMatch = reranked.stream()
                        .sorted(Comparator.comparingInt(j -> MATCH_TYPE_ORDER.getOrDefault(j.matchType(), 3)))
                        .toList();
                result.addAll(sortedByMatch);
            }
            return result;

        } catch (Exception e) {
            log.warn("[재정렬] 벡터 재정렬 실패, 폴백 정렬 적용: {}", e.getMessage());
            return fallbackSort(groups);
        }
    }

    /** 벡터 재정렬 불가 시 matchLevel 순 → createdAt 역순으로 정렬한다. */
    private List<JobSummary> fallbackSort(List<CandidateGroup> groups) {
        return groups.stream()
                .flatMap(g -> g.jobs().stream().map(j -> j.withMatchLevel(g.matchLevel().name())))
                .sorted(Comparator
                        .comparingInt((JobSummary j) -> MATCH_LEVEL_ORDER.getOrDefault(j.matchLevel(), 4))
                        .thenComparing(JobSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    // ═══════════════════════════════ 공통 유틸 ═══════════════════════════════

    /** sourceType에 따라 private/public 공고를 함께 조회한다. */
    private List<JobSummary> fetchPrivateAndPublic(SearchCondition condition, int limit) {
        List<JobSummary> privateJobs = "PUBLIC".equals(condition.sourceType()) ? List.of()
                : jobSearchRepository.searchPrivate(condition, 0, limit);
        List<JobSummary> publicJobs = "PRIVATE".equals(condition.sourceType()) ? List.of()
                : jobSearchRepository.searchPublic(condition, 0, limit);
        return Stream.concat(privateJobs.stream(), publicJobs.stream()).toList();
    }

    private SearchCondition buildCondition(MatchResult match, String method,
                                            List<RequirementGroup> exactGroups,
                                            List<RequirementGroup> semanticGroups) {
        return new SearchCondition(
                match.categories(), match.company(),
                match.location(), match.experience(),
                match.experienceLevels(), match.employmentTypes(),
                method, match.sourceType(),
                exactGroups, semanticGroups);
    }

    /**
     * UNKNOWN_STRUCTURAL 그룹용 SearchCondition.
     * 경력 목록에 "미확인"을 추가하여 경력 미확인 공고를 포함한다.
     *
     * <p>현재 deriveExperienceLevels가 "신입"/"경력" 모두 "미확인"을 포함하므로,
     * STRICT 조건에 이미 "미확인"이 포함되어 있다.
     * seenIds 필터에 의해 UNKNOWN_STRUCTURAL 그룹은 항상 빈 그룹이 되어 스킵된다.
     * 향후 deriveExperienceLevels 정책 변경 시 이 그룹이 다시 활성화될 수 있다.
     */
    private SearchCondition buildUnknownStructuralCondition(MatchResult match,
                                                             List<RequirementGroup> exactGroups,
                                                             List<RequirementGroup> semanticGroups) {
        List<String> levelsWithUnknown = new ArrayList<>(match.experienceLevels());
        levelsWithUnknown.add("미확인");
        return new SearchCondition(
                match.categories(), match.company(),
                match.location(), match.experience(),
                levelsWithUnknown, match.employmentTypes(),
                SearchCondition.METHOD_HYBRID, match.sourceType(),
                exactGroups, semanticGroups);
    }

    /** 임베딩 실패 시 null 반환 (null이면 폴백 정렬 사용). */
    private float[] tryEmbed(String text, String logPrefix) {
        try {
            return embeddingService.embedQuery(text);
        } catch (Exception e) {
            log.warn("{} 임베딩 실패, 벡터 재정렬 건너뜀: {}", logPrefix, e.getMessage());
            return null;
        }
    }

    private static long totalCandidates(List<CandidateGroup> groups) {
        return groups.stream().mapToLong(g -> g.jobs().size()).sum();
    }

    /** "PRIVATE:123" 형태의 고유 키 (PRIVATE/PUBLIC 간 ID 충돌 방지). */
    private static String uniqueId(JobSummary job) {
        return job.source() + ":" + job.id();
    }

    /** 검색 결과에 이력서 기반 매칭 점수를 부착한다. */
    private List<JobSummary> attachMatchScores(List<JobSummary> jobs, String email) {
        if (email == null || "anonymousUser".equals(email) || jobs.isEmpty()) return jobs;

        Resumes activeResume = resumesRepository.findByMemberEmailAndIsActiveTrue(email).orElse(null);
        if (activeResume == null) return jobs;

        List<Long> privateIds = jobs.stream()
                .filter(j -> "PRIVATE".equals(j.source())).map(JobSummary::id).toList();
        List<Long> publicIds = jobs.stream()
                .filter(j -> "PUBLIC".equals(j.source())).map(JobSummary::id).toList();

        Map<Long, Integer> privateScores = Map.of();
        Map<Long, Integer> publicScores = Map.of();

        if (!privateIds.isEmpty()) {
            privateScores = privateMatchScoreRepository
                    .findByResumeIdAndPrivateJobPostingIdIn(activeResume.getId(), privateIds)
                    .stream()
                    .collect(Collectors.toMap(
                            s -> s.getPrivateJobPosting().getId(),
                            PrivateMatchScore::getScore,
                            (existing, replacement) -> existing));
        }
        if (!publicIds.isEmpty()) {
            publicScores = publicMatchScoreRepository
                    .findByResumeIdAndPublicJobPostingIdIn(activeResume.getId(), publicIds)
                    .stream()
                    .collect(Collectors.toMap(
                            s -> s.getPublicJobPosting().getId(),
                            PublicMatchScore::getScore,
                            (existing, replacement) -> existing));
        }

        Map<Long, Integer> finalPrivateScores = privateScores;
        Map<Long, Integer> finalPublicScores = publicScores;

        return jobs.stream()
                .map(job -> {
                    Integer score = "PRIVATE".equals(job.source())
                            ? finalPrivateScores.get(job.id())
                            : finalPublicScores.get(job.id());
                    return job.withMatchScore(score);
                })
                .toList();
    }
}

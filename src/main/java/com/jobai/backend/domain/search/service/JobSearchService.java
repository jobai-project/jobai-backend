package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.entity.PublicMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.dto.JobSearchResponse.SearchInfo;
import com.jobai.backend.domain.search.dto.QueryExpansionResult;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 채용 공고 검색 서비스.
 *
 * <p>3단계 Retrieve-Rerank 파이프라인을 지원한다:
 * <ol>
 *   <li>Query Expansion — LLM 기반 쿼리 확장 (unmatched 토큰 → 관련 키워드 보강)</li>
 *   <li>Hybrid Search — 키워드 검색 + 벡터 검색 결과를 RRF로 병합</li>
 *   <li>Reranking — Cross-Encoder로 상위 후보 재정렬</li>
 * </ol>
 *
 * <p>각 단계는 설정으로 독립 on/off 가능하며, 전부 꺼두면 기존 동작과 동일하다.
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

    /** 하이브리드 검색 시 키워드·벡터 각각에서 가져올 고정 후보 수. */
    private static final int HYBRID_CANDIDATE_DEPTH = 200;

    private static final double VECTOR_THRESHOLD = 0.4;

    /** matchType 우선순위: EXACT(0) > SIMILAR(1). */
    private static final Map<String, Integer> MATCH_TYPE_ORDER = Map.of(
            "EXACT", 0, "SIMILAR", 1);

    /**
     * 쿼리를 분석하여 파이프라인을 실행한다.
     *
     * <pre>
     * 1. KeywordMatcher — 형태소 분석 + 구조화 토큰 추출
     * 2. QueryExpander — unmatched 토큰 LLM 확장 (Stage 1)
     * 3. 라우팅:
     *    - hybridEnabled → 키워드 + 벡터 동시 → RRF 병합 (Stage 2)
     *    - unmatched 없음 → 키워드 검색
     *    - unmatched 있음 → 벡터 검색 (확장된 쿼리로)
     * 4. SearchReranker — Cross-Encoder 재정렬 (Stage 3)
     * 5. 매칭 점수 부착
     * </pre>
     */
    public JobSearchResponse search(String query, int page, int size, String email) {
        MatchResult match = keywordMatcher.extract(query);

        log.info("키워드 분석: query={}, categories={}, company={}, location={}, experience={}, unmatchedTokens={}",
                query, match.categories(), match.company(), match.location(), match.experience(), match.unmatchedTokens());

        // Stage 1: 쿼리 확장
        QueryExpansionResult expansion = queryExpander.expand(query, match.unmatchedTokens());

        // Stage 2: 검색 라우팅
        JobSearchResponse response;

        if (hybridEnabled && embeddingEnabled) {
            log.info("하이브리드 검색 실행: query={}", query);
            response = executeHybridSearch(query, expansion, match);
        } else if (!match.hasUnmatchedTokens()) {
            log.info("구조화 검색 실행: query={}", query);
            SearchCondition condition = buildCondition(match, SearchCondition.METHOD_KEYWORD);
            response = executeTraditionalSearch(condition);
        } else if (embeddingEnabled) {
            try {
                String textForEmbedding = expansion.expandedText();
                float[] queryVector = embeddingService.embedQuery(textForEmbedding);
                log.info("벡터 검색 실행: query={}, expanded={}, dimension={}",
                        query, expansion.wasExpanded(), queryVector.length);

                SearchCondition condition = buildCondition(match, SearchCondition.METHOD_VECTOR);
                response = executeVectorSearch(queryVector, condition);
            } catch (Exception e) {
                log.warn("벡터 검색 실패, 기존 검색으로 폴백: query={}", query, e);
                SearchCondition fallback = buildCondition(match, SearchCondition.METHOD_KEYWORD);
                response = executeTraditionalSearch(fallback);
            }
        } else {
            SearchCondition fallback = buildCondition(match, SearchCondition.METHOD_KEYWORD);
            response = executeTraditionalSearch(fallback);
        }

        // Stage 3: 리랭킹 (pagination 전에 수행)
        List<JobSummary> rerankedJobs = searchReranker.rerank(query, response.jobs());

        // 리랭킹 후 pagination 적용
        int offset = page * size;
        List<JobSummary> pagedJobs = rerankedJobs.stream()
                .skip(offset)
                .limit(size)
                .toList();

        // SearchInfo에 확장 키워드 반영
        SearchInfo finalInfo = new SearchInfo(
                response.searchInfo().method(),
                response.searchInfo().matchedCategories(),
                expansion.expandedKeywords());

        // 매칭 점수 로딩
        List<JobSummary> scoredJobs = attachMatchScores(pagedJobs, email);
        return new JobSearchResponse(response.totalCount(), scoredJobs, finalInfo);
    }

    // ---------- Hybrid Search (Stage 2: RRF) ----------

    private JobSearchResponse executeHybridSearch(String query, QueryExpansionResult expansion,
                                                   MatchResult match) {
        SearchCondition condition = buildCondition(match, SearchCondition.METHOD_HYBRID);

        // 키워드 검색 (고정 후보 깊이)
        List<JobSummary> keywordPrivate = "PUBLIC".equals(condition.sourceType())
                ? List.of()
                : jobSearchRepository.searchPrivate(condition, 0, HYBRID_CANDIDATE_DEPTH);
        List<JobSummary> keywordPublic = "PRIVATE".equals(condition.sourceType())
                ? List.of()
                : jobSearchRepository.searchPublic(condition, 0, HYBRID_CANDIDATE_DEPTH);
        List<JobSummary> keywordResults = Stream.concat(keywordPrivate.stream(), keywordPublic.stream())
                .sorted(Comparator.comparingInt((JobSummary j) -> MATCH_TYPE_ORDER.getOrDefault(j.matchType(), 3))
                        .thenComparing(JobSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        // 벡터 검색 (고정 후보 깊이)
        List<JobSummary> vectorResults;
        try {
            String textForEmbedding = expansion.expandedText();
            float[] queryVector = embeddingService.embedQuery(textForEmbedding);

            List<ScoredJob> vectorPrivate = "PUBLIC".equals(condition.sourceType())
                    ? List.of()
                    : vectorSearchRepository.searchPrivateByVector(
                            queryVector, VECTOR_THRESHOLD, condition, 0, HYBRID_CANDIDATE_DEPTH);
            List<ScoredJob> vectorPublic = "PRIVATE".equals(condition.sourceType())
                    ? List.of()
                    : vectorSearchRepository.searchPublicByVector(
                            queryVector, VECTOR_THRESHOLD, condition, 0, HYBRID_CANDIDATE_DEPTH);
            vectorResults = Stream.concat(vectorPrivate.stream(), vectorPublic.stream())
                    .sorted(Comparator.comparingDouble(ScoredJob::distance))
                    .map(ScoredJob::job)
                    .toList();
        } catch (Exception e) {
            log.warn("하이브리드 검색 중 벡터 검색 실패, 키워드 결과만 사용", e);
            vectorResults = List.of();
        }

        // RRF 병합 (pagination 없이 전체 병합)
        List<JobSummary> merged = hybridSearchMerger.merge(keywordResults, vectorResults);

        // 정확한 totalCount: DB count 쿼리 사용
        long totalCount = ("PUBLIC".equals(condition.sourceType()) ? 0 : jobSearchRepository.countPrivate(condition))
                + ("PRIVATE".equals(condition.sourceType()) ? 0 : jobSearchRepository.countPublic(condition));

        SearchInfo searchInfo = new SearchInfo(
                SearchCondition.METHOD_HYBRID, condition.categories(), List.of());
        return new JobSearchResponse(totalCount, merged, searchInfo);
    }

    // ---------- 기존 검색 경로 ----------

    private JobSearchResponse executeVectorSearch(float[] queryVector, SearchCondition condition) {
        List<ScoredJob> privateResults = "PUBLIC".equals(condition.sourceType())
                ? List.of()
                : vectorSearchRepository.searchPrivateByVector(
                        queryVector, VECTOR_THRESHOLD, condition, 0, HYBRID_CANDIDATE_DEPTH);
        List<ScoredJob> publicResults = "PRIVATE".equals(condition.sourceType())
                ? List.of()
                : vectorSearchRepository.searchPublicByVector(
                        queryVector, VECTOR_THRESHOLD, condition, 0, HYBRID_CANDIDATE_DEPTH);

        List<JobSummary> allResults = Stream.concat(privateResults.stream(), publicResults.stream())
                .sorted(Comparator.comparingInt((ScoredJob s) -> MATCH_TYPE_ORDER.getOrDefault(s.job().matchType(), 3))
                        .thenComparingDouble(ScoredJob::distance))
                .map(ScoredJob::job)
                .toList();

        long totalCount = ("PUBLIC".equals(condition.sourceType()) ? 0
                        : vectorSearchRepository.countPrivateByVector(queryVector, VECTOR_THRESHOLD, condition))
                + ("PRIVATE".equals(condition.sourceType()) ? 0
                        : vectorSearchRepository.countPublicByVector(queryVector, VECTOR_THRESHOLD, condition));

        SearchInfo searchInfo = new SearchInfo(
                condition.method(), condition.categories(), List.of());
        return new JobSearchResponse(totalCount, allResults, searchInfo);
    }

    private JobSearchResponse executeTraditionalSearch(SearchCondition condition) {
        List<JobSummary> privateResults = "PUBLIC".equals(condition.sourceType())
                ? List.of()
                : jobSearchRepository.searchPrivate(condition, 0, HYBRID_CANDIDATE_DEPTH);
        List<JobSummary> publicResults = "PRIVATE".equals(condition.sourceType())
                ? List.of()
                : jobSearchRepository.searchPublic(condition, 0, HYBRID_CANDIDATE_DEPTH);

        List<JobSummary> allResults = Stream.concat(privateResults.stream(), publicResults.stream())
                .sorted(Comparator.comparingInt((JobSummary j) -> MATCH_TYPE_ORDER.getOrDefault(j.matchType(), 3))
                        .thenComparing(JobSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long totalCount = ("PUBLIC".equals(condition.sourceType()) ? 0 : jobSearchRepository.countPrivate(condition))
                + ("PRIVATE".equals(condition.sourceType()) ? 0 : jobSearchRepository.countPublic(condition));

        SearchInfo searchInfo = new SearchInfo(
                condition.method(), condition.categories(), List.of());

        return new JobSearchResponse(totalCount, allResults, searchInfo);
    }

    // ---------- 공통 유틸 ----------

    private SearchCondition buildCondition(MatchResult match, String method) {
        return new SearchCondition(
                match.categories(), match.company(),
                match.location(), match.experience(),
                match.experienceLevels(), match.employmentTypes(),
                method, match.sourceType());
    }

    /** 검색 결과에 매칭 점수를 부착한다. */
    private List<JobSummary> attachMatchScores(List<JobSummary> jobs, String email) {
        if (email == null || "anonymousUser".equals(email) || jobs.isEmpty()) {
            return jobs;
        }

        Resumes activeResume = resumesRepository.findByMemberEmailAndIsActiveTrue(email).orElse(null);
        if (activeResume == null) {
            return jobs;
        }

        List<Long> privateIds = jobs.stream()
                .filter(j -> "PRIVATE".equals(j.source()))
                .map(JobSummary::id)
                .toList();
        List<Long> publicIds = jobs.stream()
                .filter(j -> "PUBLIC".equals(j.source()))
                .map(JobSummary::id)
                .toList();

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

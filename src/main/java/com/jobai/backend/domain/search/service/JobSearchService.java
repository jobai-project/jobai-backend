package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.entity.PublicMatchScore;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.dto.JobSearchResponse.SearchInfo;
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
 * 키워드 매칭 결과에 따라 구조화 검색(카테고리/지역/경력) 또는
 * 벡터 유사도 검색(pgvector 코사인 거리)으로 라우팅한다.
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

    @Value("${search.embedding.enabled:true}")
    private boolean embeddingEnabled;

    private static final double VECTOR_THRESHOLD = 0.4;

    /** 쿼리를 분석하여 키워드 검색 또는 벡터 검색을 실행한다. */
    public JobSearchResponse search(String query, int page, int size, String email) {
        MatchResult match = keywordMatcher.extract(query);

        log.info("키워드 분석: query={}, categories={}, company={}, location={}, experience={}, unmatchedTokens={}",
                query, match.categories(), match.company(), match.location(), match.experience(), match.unmatchedTokens());

        JobSearchResponse response;

        if (!match.hasUnmatchedTokens()) {
            // 경로 A: 모든 토큰이 매칭됨 → 기존 검색
            log.info("구조화 검색 실행: query={}", query);
            SearchCondition condition = new SearchCondition(
                    match.categories(), match.company(),
                    match.location(), match.experience(),
                    match.experienceLevels(), match.employmentTypes(),
                    SearchCondition.METHOD_KEYWORD);
            response = executeTraditionalSearch(condition, page, size);
        } else if (embeddingEnabled) {
            // 경로 B: 미매칭 토큰 있음 → 벡터 검색
            try {
                float[] queryVector = embeddingService.embedQuery(query);
                log.info("벡터 검색 실행: query={}, dimension={}", query, queryVector.length);

                SearchCondition condition = new SearchCondition(
                        match.categories(), match.company(),
                        match.location(), match.experience(),
                        match.experienceLevels(), match.employmentTypes(),
                        SearchCondition.METHOD_VECTOR);

                response = executeVectorSearch(queryVector, condition, page, size);
                log.info("벡터 검색 결과: {} 건", response.jobs().size());
            } catch (Exception e) {
                log.warn("벡터 검색 실패, 기존 검색으로 폴백: query={}", query, e);
                SearchCondition fallback = new SearchCondition(
                        match.categories(), match.company(),
                        match.location(), match.experience(),
                        match.experienceLevels(), match.employmentTypes(),
                        SearchCondition.METHOD_KEYWORD);
                response = executeTraditionalSearch(fallback, page, size);
            }
        } else {
            // 벡터 검색 비활성화 시 구조화 조건만으로 폴백 검색
            SearchCondition fallback = new SearchCondition(
                    match.categories(), match.company(),
                    match.location(), match.experience(),
                    match.experienceLevels(), match.employmentTypes(),
                    SearchCondition.METHOD_KEYWORD);
            response = executeTraditionalSearch(fallback, page, size);
        }

        // 매칭 점수 로딩
        List<JobSummary> scoredJobs = attachMatchScores(response.jobs(), email);
        return new JobSearchResponse(response.totalCount(), scoredJobs, response.searchInfo());
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

    private JobSearchResponse executeVectorSearch(float[] queryVector, SearchCondition condition,
                                                   int page, int size) {
        int offset = page * size;
        int fetchLimit = offset + size;

        List<ScoredJob> privateResults = vectorSearchRepository.searchPrivateByVector(
                queryVector, VECTOR_THRESHOLD, condition, 0, fetchLimit);
        List<ScoredJob> publicResults = vectorSearchRepository.searchPublicByVector(
                queryVector, VECTOR_THRESHOLD, condition, 0, fetchLimit);

        // EXACT 먼저, SIMILAR 뒤에 → 같은 그룹 내에서 유사도순(distance 오름차순)
        List<JobSummary> allResults = Stream.concat(privateResults.stream(), publicResults.stream())
                .sorted(Comparator.comparing((ScoredJob s) -> s.job().matchType())
                        .thenComparingDouble(ScoredJob::distance))
                .skip(offset)
                .limit(size)
                .map(ScoredJob::job)
                .toList();

        long totalCount = vectorSearchRepository.countPrivateByVector(queryVector, VECTOR_THRESHOLD, condition)
                + vectorSearchRepository.countPublicByVector(queryVector, VECTOR_THRESHOLD, condition);

        SearchInfo searchInfo = new SearchInfo(
                condition.method(), condition.categories(), List.of());
        return new JobSearchResponse(totalCount, allResults, searchInfo);
    }

    private JobSearchResponse executeTraditionalSearch(SearchCondition condition, int page, int size) {
        int offset = page * size;
        int fetchLimit = offset + size;

        List<JobSummary> privateResults = jobSearchRepository.searchPrivate(condition, 0, fetchLimit);
        List<JobSummary> publicResults = jobSearchRepository.searchPublic(condition, 0, fetchLimit);

        // EXACT 먼저, SIMILAR 뒤에 → 같은 그룹 내에서 최신순
        List<JobSummary> allResults = Stream.concat(privateResults.stream(), publicResults.stream())
                .sorted(Comparator.comparing(JobSummary::matchType)
                        .thenComparing(JobSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .skip(offset)
                .limit(size)
                .toList();

        long totalCount = jobSearchRepository.countPrivate(condition)
                + jobSearchRepository.countPublic(condition);

        SearchInfo searchInfo = new SearchInfo(
                condition.method(), condition.categories(), List.of());

        return new JobSearchResponse(totalCount, allResults, searchInfo);
    }
}

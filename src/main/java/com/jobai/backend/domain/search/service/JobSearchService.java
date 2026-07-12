package com.jobai.backend.domain.search.service;

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

    @Value("${search.embedding.enabled:true}")
    private boolean embeddingEnabled;

    private static final double VECTOR_THRESHOLD = 0.4;

    /** 쿼리를 분석하여 키워드 검색 또는 벡터 검색을 실행한다. */
    public JobSearchResponse search(String query, int page, int size) {
        MatchResult match = keywordMatcher.extract(query);

        log.info("키워드 분석: query={}, categories={}, company={}, location={}, experience={}, unmatchedTokens={}",
                query, match.categories(), match.company(), match.location(), match.experience(), match.unmatchedTokens());

        if (!match.hasUnmatchedTokens()) {
            // 경로 A: 모든 토큰이 매칭됨 → 기존 검색
            log.info("구조화 검색 실행: query={}", query);
            SearchCondition condition = new SearchCondition(
                    match.categories(), match.company(),
                    match.location(), match.experience(),
                    match.experienceLevels(), match.employmentTypes(),
                    SearchCondition.METHOD_KEYWORD);
            return executeTraditionalSearch(condition, page, size);
        }

        // 경로 B: 미매칭 토큰 있음 → 벡터 검색
        if (embeddingEnabled) {
            try {
                float[] queryVector = embeddingService.embedQuery(query);
                log.info("벡터 검색 실행: query={}, dimension={}", query, queryVector.length);

                SearchCondition condition = new SearchCondition(
                        match.categories(), match.company(),
                        match.location(), match.experience(),
                        match.experienceLevels(), match.employmentTypes(),
                        SearchCondition.METHOD_VECTOR);

                JobSearchResponse result = executeVectorSearch(queryVector, condition, page, size);
                log.info("벡터 검색 결과: {} 건", result.jobs().size());
                return result;
            } catch (Exception e) {
                log.warn("벡터 검색 실패, 기존 검색으로 폴백: query={}", query, e);
            }
        }

        // 벡터 검색 비활성화 또는 실패 시 구조화 조건만으로 폴백 검색
        // unmatchedTokens는 의미 검색용이므로 LIKE 키워드로 사용하지 않는다
        SearchCondition fallback = new SearchCondition(
                match.categories(), match.company(),
                match.location(), match.experience(),
                match.experienceLevels(), match.employmentTypes(),
                SearchCondition.METHOD_KEYWORD);
        return executeTraditionalSearch(fallback, page, size);
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

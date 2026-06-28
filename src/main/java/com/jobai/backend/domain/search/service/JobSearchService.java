package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.dto.JobSearchResponse.SearchInfo;
import com.jobai.backend.domain.search.repository.JobSearchRepository;
import com.jobai.backend.domain.search.repository.VectorSearchRepository;
import com.jobai.backend.domain.search.service.KeywordMatcher.MatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public JobSearchResponse search(String query, int page, int size) {
        MatchResult match = keywordMatcher.extract(query);

        log.info("키워드 분석: query={}, categories={}, location={}, experience={}, unmatchedTokens={}",
                query, match.categories(), match.location(), match.experience(), match.unmatchedTokens());

        if (!match.hasUnmatchedTokens()) {
            // 경로 A: 모든 토큰이 매칭됨 → 기존 검색
            log.info("구조화 검색 실행: query={}", query);
            SearchCondition condition = new SearchCondition(
                    match.categories(), List.of(), List.of(),
                    match.location(), match.experience(),
                    SearchCondition.METHOD_KEYWORD);
            return executeTraditionalSearch(condition, page, size);
        }

        // 경로 B: 미매칭 토큰 있음 → 벡터 검색
        if (embeddingEnabled) {
            try {
                float[] queryVector = embeddingService.embedQuery(query);
                log.info("벡터 검색 실행: query={}, dimension={}", query, queryVector.length);

                SearchCondition condition = new SearchCondition(
                        match.categories(), List.of(), List.of(),
                        match.location(), match.experience(),
                        SearchCondition.METHOD_VECTOR);

                JobSearchResponse result = executeVectorSearch(queryVector, condition, page, size);
                log.info("벡터 검색 결과: {} 건", result.jobs().size());
                return result;
            } catch (Exception e) {
                log.warn("벡터 검색 실패, 기존 검색으로 폴백: query={}", query, e);
            }
        }

        // 벡터 검색 비활성화 또는 실패 시 기존 검색 폴백
        SearchCondition fallback = new SearchCondition(
                match.categories(), List.of(), List.of(),
                match.location(), match.experience(),
                SearchCondition.METHOD_KEYWORD);
        return executeTraditionalSearch(fallback, page, size);
    }

    private JobSearchResponse executeVectorSearch(float[] queryVector, SearchCondition condition,
                                                   int page, int size) {
        int offset = page * size;

        List<JobSummary> privateResults = vectorSearchRepository.searchPrivateByVector(
                queryVector, VECTOR_THRESHOLD, condition, offset, size);
        List<JobSummary> publicResults = vectorSearchRepository.searchPublicByVector(
                queryVector, VECTOR_THRESHOLD, condition, offset, size);

        List<JobSummary> allResults = mergeByCreatedAtDesc(privateResults, publicResults, size);

        long totalCount = vectorSearchRepository.countPrivateByVector(queryVector, VECTOR_THRESHOLD, condition)
                + vectorSearchRepository.countPublicByVector(queryVector, VECTOR_THRESHOLD, condition);

        SearchInfo searchInfo = new SearchInfo(
                condition.method(), condition.categories(), List.of());
        return new JobSearchResponse(totalCount, allResults, searchInfo);
    }

    private JobSearchResponse executeTraditionalSearch(SearchCondition condition, int page, int size) {
        int offset = page * size;

        List<JobSummary> privateResults = jobSearchRepository.searchPrivate(condition, offset, size);
        List<JobSummary> publicResults = jobSearchRepository.searchPublic(condition, offset, size);

        List<JobSummary> allResults = mergeByCreatedAtDesc(privateResults, publicResults, size);

        long totalCount = jobSearchRepository.countPrivate(condition)
                + jobSearchRepository.countPublic(condition);

        SearchInfo searchInfo = new SearchInfo(
                condition.method(), condition.categories(), List.of());

        return new JobSearchResponse(totalCount, allResults, searchInfo);
    }

    private List<JobSummary> mergeByCreatedAtDesc(List<JobSummary> listA, List<JobSummary> listB, int limit) {
        return Stream.concat(listA.stream(), listB.stream())
                .sorted(Comparator.comparing(JobSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }
}

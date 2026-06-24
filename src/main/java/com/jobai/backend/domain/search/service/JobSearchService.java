package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.dto.JobSearchResponse.SearchInfo;
import com.jobai.backend.domain.search.repository.JobSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobSearchService {

    private final KeywordMatcher keywordMatcher;
    private final QueryInterpreter queryInterpreter;
    private final JobSearchRepository jobSearchRepository;

    public JobSearchResponse search(String query, int page, int size) {
        // 1. 룰 기반 매칭 시도
        Optional<SearchCondition> ruleMatch = keywordMatcher.match(query);
        SearchCondition condition;

        if (ruleMatch.isPresent()) {
            condition = ruleMatch.get();
            log.info("룰 기반 매칭 성공: query={}, categories={}", query, condition.categories());
        } else {
            // 2. LLM 의미 해석
            log.info("LLM 의미 해석 시작: query={}", query);
            try {
                condition = queryInterpreter.interpret(query);
                log.info("LLM 의미 해석 완료: categories={}, titleKeywords={}",
                        condition.categories(), condition.titleKeywords());
            } catch (Exception e) {
                // 3. LLM 실패 시 원본 query로 title LIKE 검색 fallback
                log.warn("LLM 호출 실패, 원본 쿼리로 fallback: query={}", query, e);
                condition = buildFallbackCondition(query);
            }
        }

        // 3. DB 검색 수행
        int halfSize = Math.max(size / 2, 1);
        int publicSize = Math.max(size - halfSize, 0);
        int privateOffset = page * halfSize;
        int publicOffset = page * publicSize;

        List<JobSummary> privateResults = jobSearchRepository.searchPrivate(condition, privateOffset, halfSize);
        List<JobSummary> publicResults = jobSearchRepository.searchPublic(condition, publicOffset, publicSize);

        // primary 결과가 부족하고 fallback 카테고리가 있으면 추가 검색
        List<JobSummary> allResults = new ArrayList<>(privateResults);
        allResults.addAll(publicResults);

        boolean usedFallback = false;
        SearchCondition fallback = null;

        if (allResults.size() < size && condition.fallbackCategories() != null
                && !condition.fallbackCategories().isEmpty()) {
            fallback = new SearchCondition(
                    condition.fallbackCategories(),
                    List.of(),
                    List.of(),
                    condition.location(),
                    condition.experience(),
                    condition.method()
            );

            Set<Long> seenIds = allResults.stream()
                    .map(JobSummary::id)
                    .collect(Collectors.toSet());

            int remaining = size - allResults.size();
            int fallbackHalf = Math.max(remaining / 2, 1);

            List<JobSummary> fallbackPrivate = jobSearchRepository.searchPrivate(fallback, 0, fallbackHalf + seenIds.size())
                    .stream()
                    .filter(job -> !seenIds.contains(job.id()))
                    .limit(fallbackHalf)
                    .toList();

            List<JobSummary> fallbackPublic = jobSearchRepository.searchPublic(fallback, 0, (remaining - fallbackHalf) + seenIds.size())
                    .stream()
                    .filter(job -> !seenIds.contains(job.id()))
                    .limit(remaining - fallbackHalf)
                    .toList();

            allResults.addAll(fallbackPrivate);
            allResults.addAll(fallbackPublic);
            usedFallback = true;
        }

        long totalCount = jobSearchRepository.countPrivate(condition)
                + jobSearchRepository.countPublic(condition);
        if (usedFallback) {
            totalCount += jobSearchRepository.countPrivate(fallback)
                    + jobSearchRepository.countPublic(fallback);
        }

        // 4. 응답 조립
        List<String> matchedCategories = new ArrayList<>(condition.categories());
        if (condition.fallbackCategories() != null) {
            matchedCategories.addAll(condition.fallbackCategories());
        }

        List<String> expandedKeywords = SearchCondition.METHOD_KEYWORD.equals(condition.method())
                ? List.of()
                : condition.titleKeywords();

        SearchInfo searchInfo = new SearchInfo(condition.method(), matchedCategories, expandedKeywords);

        return new JobSearchResponse(totalCount, allResults, searchInfo);
    }

    private SearchCondition buildFallbackCondition(String query) {
        List<String> keywords = Arrays.stream(query.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();

        return new SearchCondition(
                List.of(),
                List.of(),
                keywords,
                null,
                null,
                SearchCondition.METHOD_AI_FALLBACK
        );
    }
}

package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.dto.LatestJobsResponse;
import com.jobai.backend.domain.home.dto.LatestJobsResponse.LatestJob;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import com.jobai.backend.global.cache.CacheKeyGenerator;
import com.jobai.backend.global.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 비로그인 사용자도 볼 수 있는 최신순 공고 피드.
 * 회원/매칭점수와 무관하므로 HomeRecommendationService와 분리된 별도 서비스로 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LatestJobsService {

    // HomeRecommendationService와 동일한 안전 상한 정책. 두 소스를 병합해 다시 정렬하므로 필요하다.
    private static final int CANDIDATE_FETCH_CAP = 1000;

    private static final List<String> DEFAULT_COMPANY_TYPES = List.of("PUBLIC", "PRIVATE");

    private final HomeJobCandidateRepository candidateRepository;

    @Cacheable(cacheNames = CacheNames.LATEST_JOBS,
            key = "T(com.jobai.backend.global.cache.CacheKeyGenerator).buildKey(#companyTypes, #locations, #employmentTypes, #offset, #size)")
    public LatestJobsResponse getLatestJobs(
            List<String> companyTypes,
            List<String> locations,
            List<String> employmentTypes,
            int offset,
            int size
    ) {
        List<String> effectiveCompanyTypes = (companyTypes == null || companyTypes.isEmpty())
                ? DEFAULT_COMPANY_TYPES
                : companyTypes;
        boolean includePublic = effectiveCompanyTypes.contains("PUBLIC");
        boolean includePrivate = effectiveCompanyTypes.contains("PRIVATE");

        List<JobCandidate> publicCandidates = includePublic
                ? candidateRepository.findPublicCandidates(locations, employmentTypes, CANDIDATE_FETCH_CAP)
                : List.of();
        List<JobCandidate> privateCandidates = includePrivate
                ? candidateRepository.findPrivateCandidates(locations, employmentTypes, CANDIDATE_FETCH_CAP)
                : List.of();

        List<JobCandidate> sorted = Stream.concat(publicCandidates.stream(), privateCandidates.stream())
                .sorted(Comparator.comparing(JobCandidate::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long totalCount = sorted.size();
        List<LatestJob> jobs = sorted.stream()
                .skip(offset)
                .limit(size)
                .map(this::toLatestJob)
                .toList();

        return new LatestJobsResponse(totalCount, offset + size < totalCount, jobs);
    }

    private LatestJob toLatestJob(JobCandidate candidate) {
        return LatestJob.of(
                candidate.id(),
                candidate.source(),
                candidate.companyName(),
                candidate.title(),
                candidate.deadline(),
                candidate.location(),
                candidate.employmentType()
        );
    }
}

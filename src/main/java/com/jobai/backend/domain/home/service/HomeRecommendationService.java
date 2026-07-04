package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.HomeRecommendationResponse;
import com.jobai.backend.domain.home.dto.HomeRecommendationResponse.RecommendedJob;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeRecommendationService {

    // 매칭점수 정렬을 애플리케이션 레벨에서 수행하므로, 필터된 후보 전체를 담을 안전 상한.
    // 현재 데이터 규모(공기업 987건 수준)에서는 충분하며, 데이터가 크게 늘면 재검토 필요.
    private static final int CANDIDATE_FETCH_CAP = 1000;

    private static final List<String> DEFAULT_COMPANY_TYPES = List.of("PUBLIC", "PRIVATE");

    private final MemberRepository memberRepository;
    private final HomeJobCandidateRepository candidateRepository;
    private final JobMatchScorer jobMatchScorer;

    public HomeRecommendationResponse getRecommendedJobs(
            String email,
            List<String> companyTypes,
            List<String> locations,
            List<String> employmentTypes,
            int offset,
            int size
    ) {
        memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));

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

        long totalCount = (includePublic ? candidateRepository.countPublicCandidates(locations, employmentTypes) : 0)
                + (includePrivate ? candidateRepository.countPrivateCandidates(locations, employmentTypes) : 0);

        List<RecommendedJob> jobs = Stream.concat(publicCandidates.stream(), privateCandidates.stream())
                .sorted(Comparator
                        .comparingInt((JobCandidate c) -> jobMatchScorer.mockScore(c.source(), c.id()))
                        .reversed()
                        .thenComparing(JobCandidate::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .skip(offset)
                .limit(size)
                .map(this::toRecommendedJob)
                .toList();

        boolean hasMore = offset + size < totalCount;

        return new HomeRecommendationResponse(totalCount, hasMore, jobs);
    }

    private RecommendedJob toRecommendedJob(JobCandidate candidate) {
        int score = jobMatchScorer.mockScore(candidate.source(), candidate.id());
        return RecommendedJob.of(
                candidate.id(),
                candidate.source(),
                candidate.companyName(),
                candidate.title(),
                score,
                candidate.deadline(),
                candidate.location(),
                candidate.employmentType()
        );
    }
}

package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.HomeRecommendationResponse;
import com.jobai.backend.domain.home.dto.HomeRecommendationResponse.RecommendedJob;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository.ScoredJobCandidate;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.PreferredJob;
import com.jobai.backend.domain.member.entity.PreferredRegion;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeRecommendationService {

    private static final List<String> DEFAULT_COMPANY_TYPES = List.of("PUBLIC", "PRIVATE");

    // Notification.createDefault()의 기본값과 동일. 온보딩 전이라 설정 행이 없는 회원에게 적용.
    private static final int DEFAULT_MATCH_SCORE_THRESHOLD = 70;

    private final MemberRepository memberRepository;
    private final HomeJobCandidateRepository candidateRepository;
    private final NotificationRepository notificationRepository;
    private final ResumesRepository resumesRepository;

    public HomeRecommendationResponse getRecommendedJobs(
            String email,
            List<String> companyTypes,
            List<String> locations,
            List<String> employmentTypes,
            int offset,
            int size
    ) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));

        List<String> effectiveCompanyTypes = (companyTypes == null || companyTypes.isEmpty())
                ? DEFAULT_COMPANY_TYPES
                : companyTypes;
        boolean includePublic = effectiveCompanyTypes.contains("PUBLIC");
        boolean includePrivate = effectiveCompanyTypes.contains("PRIVATE");

        boolean hasPreferences = hasPreferences(member);
        if (hasPreferences) {
            Optional<Resumes> activeResume = resumesRepository.findByMemberEmailAndIsActiveTrue(email);
            if (activeResume.isPresent()) {
                return buildScoredResponse(
                        email, activeResume.get(), includePublic, includePrivate,
                        locations, employmentTypes, offset, size
                );
            }
        }

        List<String> preferredJobCategories = hasPreferences
                ? member.getPrefJobs().stream().map(PreferredJob::getJobCategory).toList()
                : List.of();
        List<String> preferredLocations = hasPreferences
                ? member.getPrefLocations().stream().map(PreferredRegion::getLocation).toList()
                : List.of();
        int fetchLimit = calculateFetchLimit(offset, size);
        List<JobCandidate> publicCandidates = includePublic
                ? candidateRepository.findLatestPublicCandidates(
                        locations, employmentTypes, preferredJobCategories, preferredLocations, fetchLimit)
                : List.of();
        List<JobCandidate> privateCandidates = includePrivate
                ? candidateRepository.findLatestPrivateCandidates(
                        locations, employmentTypes, preferredJobCategories, preferredLocations, fetchLimit)
                : List.of();
        List<JobCandidate> allCandidates = Stream.concat(publicCandidates.stream(), privateCandidates.stream()).toList();
        long totalCount = 0;
        if (includePublic) {
            totalCount += candidateRepository.countLatestPublicCandidates(
                    locations, employmentTypes, preferredJobCategories, preferredLocations);
        }
        if (includePrivate) {
            totalCount += candidateRepository.countLatestPrivateCandidates(
                    locations, employmentTypes, preferredJobCategories, preferredLocations);
        }
        return buildLatestResponse(allCandidates, totalCount, offset, size);
    }

    // 온보딩 완료 + 희망직무/지역 중 하나라도 설정했는지 확인
    private boolean hasPreferences(Member member) {
        return member.isOnboardingCompleted()
                && (!member.getPrefJobs().isEmpty() || !member.getPrefLocations().isEmpty());
    }

    // 매칭 근거가 있는 회원: 적합도 기준 이상만 남기고 매칭점수 내림차순 정렬
    private HomeRecommendationResponse buildScoredResponse(
            String email,
            Resumes activeResume,
            boolean includePublic,
            boolean includePrivate,
            List<String> locations,
            List<String> employmentTypes,
            int offset,
            int size
    ) {
        int matchScoreThreshold = resolveMatchScoreThreshold(email);
        int fetchLimit = calculateFetchLimit(offset, size);
        List<ScoredJobCandidate> publicCandidates = includePublic
                ? candidateRepository.findScoredPublicCandidates(
                        activeResume.getId(), locations, employmentTypes, matchScoreThreshold, fetchLimit)
                : List.of();
        List<ScoredJobCandidate> privateCandidates = includePrivate
                ? candidateRepository.findScoredPrivateCandidates(
                        activeResume.getId(), locations, employmentTypes, matchScoreThreshold, fetchLimit)
                : List.of();

        List<ScoredJobCandidate> scoredCandidates = Stream.concat(
                        publicCandidates.stream(), privateCandidates.stream())
                .sorted(Comparator
                        .comparingInt(ScoredJobCandidate::score)
                        .reversed()
                        .thenComparing(sc -> sc.candidate().createdAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long totalCount = 0;
        if (includePublic) {
            totalCount += candidateRepository.countScoredPublicCandidates(
                    activeResume.getId(), locations, employmentTypes, matchScoreThreshold);
        }
        if (includePrivate) {
            totalCount += candidateRepository.countScoredPrivateCandidates(
                    activeResume.getId(), locations, employmentTypes, matchScoreThreshold);
        }
        List<RecommendedJob> jobs = scoredCandidates.stream()
                .skip(offset)
                .limit(size)
                .map(sc -> toRecommendedJob(sc.candidate(), sc.score()))
                .toList();

        return new HomeRecommendationResponse(totalCount, offset + size < totalCount, jobs);
    }

    private int calculateFetchLimit(int offset, int size) {
        long requested = (long) Math.max(offset, 0) + Math.max(size, 0);
        return (int) Math.min(Math.max(requested, 1L), Integer.MAX_VALUE);
    }

    // 온보딩 미완료 또는 희망직무/지역 미설정: 최신순으로만 노출, matchScore는 null
    private HomeRecommendationResponse buildLatestResponse(
            List<JobCandidate> candidates, long totalCount, int offset, int size
    ) {
        List<JobCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(JobCandidate::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<RecommendedJob> jobs = sorted.stream()
                .skip(offset)
                .limit(size)
                .map(c -> toRecommendedJob(c, null))
                .toList();

        return new HomeRecommendationResponse(totalCount, offset + size < totalCount, jobs);
    }

    private int resolveMatchScoreThreshold(String email) {
        return notificationRepository.findByMemberEmail(email)
                .map(Notification::getMatchScoreThreshold)
                .orElse(DEFAULT_MATCH_SCORE_THRESHOLD);
    }

    private RecommendedJob toRecommendedJob(JobCandidate candidate, Integer score) {
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

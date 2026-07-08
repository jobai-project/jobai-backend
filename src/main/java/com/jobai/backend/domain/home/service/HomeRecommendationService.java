package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.HomeRecommendationResponse;
import com.jobai.backend.domain.home.dto.HomeRecommendationResponse.RecommendedJob;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeRecommendationService {

    // 매칭점수 정렬/필터를 애플리케이션 레벨에서 수행하므로, 필터된 후보 전체를 담을 안전 상한.
    // 현재 데이터 규모(공기업 987건 수준)에서는 충분하며, 데이터가 크게 늘면 재검토 필요.
    private static final int CANDIDATE_FETCH_CAP = 1000;

    private static final List<String> DEFAULT_COMPANY_TYPES = List.of("PUBLIC", "PRIVATE");

    // Notification.createDefault()의 기본값과 동일. 온보딩 전이라 설정 행이 없는 회원에게 적용.
    private static final int DEFAULT_MATCH_SCORE_THRESHOLD = 70;

    private final MemberRepository memberRepository;
    private final HomeJobCandidateRepository candidateRepository;
    private final NotificationRepository notificationRepository;
    private final JobMatchScorer jobMatchScorer;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;

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

        List<JobCandidate> publicCandidates = includePublic
                ? candidateRepository.findPublicCandidates(locations, employmentTypes, CANDIDATE_FETCH_CAP)
                : List.of();
        List<JobCandidate> privateCandidates = includePrivate
                ? candidateRepository.findPrivateCandidates(locations, employmentTypes, CANDIDATE_FETCH_CAP)
                : List.of();
        List<JobCandidate> allCandidates = Stream.concat(publicCandidates.stream(), privateCandidates.stream()).toList();

        return hasMatchingBasis(member)
                ? buildScoredResponse(email, allCandidates, offset, size)
                : buildLatestResponse(allCandidates, offset, size);
    }

    // 온보딩을 완료했고 희망직무/지역 중 하나라도 설정한 회원만 매칭점수를 계산할 근거가 있다고 본다.
    private boolean hasMatchingBasis(Member member) {
        return member.isOnboardingCompleted()
                && (!member.getPrefJobs().isEmpty() || !member.getPrefLocations().isEmpty());
    }

    // 매칭 근거가 있는 회원: 적합도 기준 이상만 남기고 매칭점수 내림차순 정렬
    private HomeRecommendationResponse buildScoredResponse(
            String email, List<JobCandidate> candidates, int offset, int size
    ) {
        int matchScoreThreshold = resolveMatchScoreThreshold(email);
        Map<Long, Integer> privateScoreMap = loadPrivateScores(email, candidates);

        List<ScoredCandidate> scoredCandidates = candidates.stream()
                .map(c -> new ScoredCandidate(c, resolveScore(c, privateScoreMap)))
                .filter(sc -> sc.score() >= matchScoreThreshold)
                .sorted(Comparator
                        .comparingInt(ScoredCandidate::score)
                        .reversed()
                        .thenComparing(sc -> sc.candidate().createdAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long totalCount = scoredCandidates.size();
        List<RecommendedJob> jobs = scoredCandidates.stream()
                .skip(offset)
                .limit(size)
                .map(sc -> toRecommendedJob(sc.candidate(), sc.score()))
                .toList();

        return new HomeRecommendationResponse(totalCount, offset + size < totalCount, jobs);
    }

    /**
     * 활성 이력서가 있으면 PRIVATE 공고의 AI 매칭 점수를 벌크 조회하여 Map으로 반환한다.
     * 활성 이력서가 없거나 PRIVATE 공고가 없으면 빈 Map을 반환한다.
     */
    private Map<Long, Integer> loadPrivateScores(String email, List<JobCandidate> candidates) {
        List<Long> privateJobIds = candidates.stream()
                .filter(c -> "PRIVATE".equals(c.source()))
                .map(JobCandidate::id)
                .toList();
        if (privateJobIds.isEmpty()) {
            return Map.of();
        }

        return resumesRepository.findByMemberEmailAndIsActiveTrue(email)
                .map(resume -> privateMatchScoreRepository
                        .findByResumeIdAndPrivateJobPostingIdIn(resume.getId(), privateJobIds)
                        .stream()
                        .collect(Collectors.toMap(
                                s -> s.getPrivateJobPosting().getId(),
                                PrivateMatchScore::getScore
                        )))
                .orElse(Map.of());
    }

    private int resolveScore(JobCandidate candidate, Map<Long, Integer> privateScoreMap) {
        if ("PRIVATE".equals(candidate.source())) {
            Integer realScore = privateScoreMap.get(candidate.id());
            if (realScore != null) {
                return realScore;
            }
        }
        return jobMatchScorer.mockScore(candidate.source(), candidate.id());
    }

    // 매칭 근거가 없는 회원(온보딩 미완료 또는 희망직무/지역 미설정): 최신순으로만 노출, matchScore는 null
    private HomeRecommendationResponse buildLatestResponse(List<JobCandidate> candidates, int offset, int size) {
        List<JobCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(JobCandidate::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long totalCount = sorted.size();
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

    private record ScoredCandidate(JobCandidate candidate, int score) {
    }
}

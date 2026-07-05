package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.HomeRecommendationResponse;
import com.jobai.backend.domain.home.dto.HomeRecommendationResponse.RecommendedJob;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
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

        int matchScoreThreshold = resolveMatchScoreThreshold(email);

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

        // 매칭점수는 애플리케이션에서 계산하는 값이라, 임계값 이상만 남기는 필터도 정렬 전에 여기서 수행한다.
        // 이 필터를 통과한 개수를 기준으로 totalCount/hasMore를 계산해야 프론트 페이지네이션이 어긋나지 않는다.
        List<ScoredCandidate> scoredCandidates = Stream.concat(publicCandidates.stream(), privateCandidates.stream())
                .map(c -> new ScoredCandidate(c, jobMatchScorer.mockScore(c.source(), c.id())))
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

        boolean hasMore = offset + size < totalCount;

        return new HomeRecommendationResponse(totalCount, hasMore, jobs);
    }

    private int resolveMatchScoreThreshold(String email) {
        return notificationRepository.findByMemberEmail(email)
                .map(Notification::getMatchScoreThreshold)
                .orElse(DEFAULT_MATCH_SCORE_THRESHOLD);
    }

    private RecommendedJob toRecommendedJob(JobCandidate candidate, int score) {
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

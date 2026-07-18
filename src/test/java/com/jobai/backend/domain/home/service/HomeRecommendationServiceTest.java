package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.HomeRecommendationResponse;
import com.jobai.backend.domain.home.dto.HomeRecommendationResponse.RecommendedJob;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.entity.PublicMatchScore;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.jobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.PreferredJob;
import com.jobai.backend.domain.member.entity.PreferredRegion;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HomeRecommendationServiceTest {

    private static final String EMAIL = "test@jobai.com";

    private MemberRepository memberRepository;
    private HomeJobCandidateRepository candidateRepository;
    private NotificationRepository notificationRepository;
    private JobMatchScorer jobMatchScorer;
    private ResumesRepository resumesRepository;
    private PrivateMatchScoreRepository privateMatchScoreRepository;
    private PublicMatchScoreRepository publicMatchScoreRepository;
    private HomeRecommendationService service;

    @BeforeEach
    void setUp() {
        memberRepository = Mockito.mock(MemberRepository.class);
        candidateRepository = Mockito.mock(HomeJobCandidateRepository.class);
        notificationRepository = Mockito.mock(NotificationRepository.class);
        jobMatchScorer = new JobMatchScorer();
        resumesRepository = Mockito.mock(ResumesRepository.class);
        privateMatchScoreRepository = Mockito.mock(PrivateMatchScoreRepository.class);
        publicMatchScoreRepository = Mockito.mock(PublicMatchScoreRepository.class);
        service = new HomeRecommendationService(
                memberRepository, candidateRepository, notificationRepository,
                jobMatchScorer, resumesRepository, privateMatchScoreRepository, publicMatchScoreRepository
        );

        // 기본값: 온보딩 완료 + 희망직무 설정 + 활성 이력서 있음 → buildScoredResponse 경로
        Member memberWithBasis = Member.builder().id(1L).email(EMAIL).onboardingCompleted(true).build();
        memberWithBasis.getPrefJobs().add(new PreferredJob(memberWithBasis, "백엔드"));
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(memberWithBasis));

        Resumes activeResume = Resumes.builder().id(10L).isActive(true).build();
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(activeResume));

        // 기본값: 임계값 0으로 두어 매칭점수 필터링과 무관하게 동작하도록 한다.
        when(notificationRepository.findByMemberEmail(EMAIL))
                .thenReturn(Optional.of(Notification.builder().matchScoreThreshold(0).build()));

        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of());
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예외를 던지고 후보 조회를 하지 않는다")
    void 회원없으면_예외() {
        when(memberRepository.findByEmail("ghost@jobai.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecommendedJobs("ghost@jobai.com", null, null, null, 0, 18))
                .isInstanceOf(GeneralException.class);

        verifyNoInteractions(candidateRepository);
    }

    @Test
    @DisplayName("companyTypes 미지정 시 공기업/사기업 둘 다 조회한다")
    void companyTypes_미지정시_전체조회() {
        service.getRecommendedJobs(EMAIL, null, null, null, 0, 18);

        Mockito.verify(candidateRepository).findPublicCandidates(any(), any(), anyInt());
        Mockito.verify(candidateRepository).findPrivateCandidates(any(), any(), anyInt());
    }

    @Test
    @DisplayName("companyTypes=PUBLIC만 지정하면 사기업 리포지토리는 호출하지 않는다")
    void companyTypes_PUBLIC만_지정() {
        service.getRecommendedJobs(EMAIL, List.of("PUBLIC"), null, null, 0, 18);

        Mockito.verify(candidateRepository).findPublicCandidates(any(), any(), anyInt());
        Mockito.verify(candidateRepository, Mockito.never()).findPrivateCandidates(any(), any(), anyInt());
    }

    @Test
    @DisplayName("companyTypes=PRIVATE만 지정하면 공기업 리포지토리는 호출하지 않는다")
    void companyTypes_PRIVATE만_지정() {
        service.getRecommendedJobs(EMAIL, List.of("PRIVATE"), null, null, 0, 18);

        Mockito.verify(candidateRepository).findPrivateCandidates(any(), any(), anyInt());
        Mockito.verify(candidateRepository, Mockito.never()).findPublicCandidates(any(), any(), anyInt());
    }

    @Test
    @DisplayName("공기업/사기업 후보를 매칭점수 내림차순으로 병합 정렬한다")
    void 매칭점수_내림차순_병합정렬() {
        JobCandidate pub1 = candidate(1L, "PUBLIC");
        JobCandidate pub2 = candidate(2L, "PUBLIC");
        JobCandidate prv1 = candidate(10L, "PRIVATE");
        JobCandidate prv2 = candidate(20L, "PRIVATE");

        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of(pub1, pub2));
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(prv1, prv2));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, null, null, null, 0, 10);

        List<Integer> expectedOrder = List.of(pub1, pub2, prv1, prv2).stream()
                .map(c -> jobMatchScorer.mockScore(c.source(), c.id()))
                .sorted(Comparator.reverseOrder())
                .toList();

        assertThat(response.jobs()).extracting(RecommendedJob::matchScore).isEqualTo(expectedOrder);
        assertThat(response.jobs()).isSortedAccordingTo(
                Comparator.comparingInt(RecommendedJob::matchScore).reversed());
    }

    @Test
    @DisplayName("totalCount와 offset+size 비교로 hasMore를 계산한다")
    void hasMore_계산() {
        List<JobCandidate> publicCandidates = java.util.stream.LongStream.rangeClosed(1, 20)
                .mapToObj(id -> candidate(id, "PUBLIC"))
                .toList();
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(publicCandidates);

        HomeRecommendationResponse notLast = service.getRecommendedJobs(EMAIL, null, null, null, 0, 18);
        HomeRecommendationResponse last = service.getRecommendedJobs(EMAIL, null, null, null, 18, 18);

        assertThat(notLast.hasMore()).isTrue();
        assertThat(last.hasMore()).isFalse();
        assertThat(notLast.totalCount()).isEqualTo(20L);
    }

    @Test
    @DisplayName("offset/size로 병합된 후보 목록을 잘라 반환한다")
    void offset_size_페이지네이션() {
        List<JobCandidate> publicCandidates = List.of(
                candidate(1L, "PUBLIC"), candidate(2L, "PUBLIC"), candidate(3L, "PUBLIC")
        );
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(publicCandidates);

        HomeRecommendationResponse firstPage = service.getRecommendedJobs(EMAIL, null, null, null, 0, 2);
        HomeRecommendationResponse secondPage = service.getRecommendedJobs(EMAIL, null, null, null, 2, 2);

        assertThat(firstPage.jobs()).hasSize(2);
        assertThat(secondPage.jobs()).hasSize(1);

        List<Long> firstIds = firstPage.jobs().stream().map(RecommendedJob::id).toList();
        List<Long> secondIds = secondPage.jobs().stream().map(RecommendedJob::id).toList();
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }

    @Test
    @DisplayName("설정한 적합도 기준 미만인 공고는 결과와 totalCount에서 제외된다")
    void 적합도_기준_미만_공고_제외() {
        List<JobCandidate> candidates = java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(id -> candidate(id, "PUBLIC"))
                .toList();
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(candidates);

        List<Integer> scores = candidates.stream()
                .map(c -> jobMatchScorer.mockScore(c.source(), c.id()))
                .sorted(Comparator.reverseOrder())
                .toList();
        int threshold = scores.get(scores.size() / 2); // 절반 정도만 통과하도록 임계값 선택
        long expectedCount = scores.stream().filter(s -> s >= threshold).count();

        when(notificationRepository.findByMemberEmail(EMAIL))
                .thenReturn(Optional.of(Notification.builder().matchScoreThreshold(threshold).build()));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, null, null, null, 0, 100);

        assertThat(response.totalCount()).isEqualTo(expectedCount);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isGreaterThanOrEqualTo(threshold));
    }

    @Test
    @DisplayName("알림 설정이 없는 회원은 기본 임계값(70)이 적용된다")
    void 알림설정_없으면_기본임계값_적용() {
        when(notificationRepository.findByMemberEmail(EMAIL)).thenReturn(Optional.empty());

        List<JobCandidate> candidates = java.util.stream.LongStream.rangeClosed(1, 10)
                .mapToObj(id -> candidate(id, "PUBLIC"))
                .toList();
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(candidates);

        long expectedCount = candidates.stream()
                .filter(c -> jobMatchScorer.mockScore(c.source(), c.id()) >= 70)
                .count();

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, null, null, null, 0, 100);

        assertThat(response.totalCount()).isEqualTo(expectedCount);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isGreaterThanOrEqualTo(70));
    }

    @Test
    @DisplayName("온보딩 미완료 회원은 matchScore가 null이고 최신순으로 정렬된다")
    void 온보딩_미완료_회원은_최신순() {
        Member notOnboarded = Member.builder().id(2L).email(EMAIL).onboardingCompleted(false).build();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(notOnboarded));

        JobCandidate older = candidateWithCreatedAt(1L, "PUBLIC", LocalDateTime.of(2026, 1, 1, 0, 0));
        JobCandidate newer = candidateWithCreatedAt(2L, "PUBLIC", LocalDateTime.of(2026, 6, 1, 0, 0));
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of(older, newer));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, null, null, null, 0, 10);

        assertThat(response.jobs()).extracting(RecommendedJob::id).containsExactly(2L, 1L);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isNull());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("온보딩은 완료했지만 희망직무/지역을 하나도 설정하지 않은 회원도 최신순으로 정렬된다")
    void 희망조건_미설정_회원도_최신순() {
        Member onboardedNoPrefs = Member.builder().id(3L).email(EMAIL).onboardingCompleted(true).build();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(onboardedNoPrefs));

        JobCandidate older = candidateWithCreatedAt(1L, "PUBLIC", LocalDateTime.of(2026, 1, 1, 0, 0));
        JobCandidate newer = candidateWithCreatedAt(2L, "PUBLIC", LocalDateTime.of(2026, 6, 1, 0, 0));
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of(older, newer));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, null, null, null, 0, 10);

        assertThat(response.jobs()).extracting(RecommendedJob::id).containsExactly(2L, 1L);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isNull());
    }

    // ── PRIVATE 실점수 연동 테스트 ──

    @Test
    @DisplayName("활성 이력서가 있으면 PRIVATE 공고에 AI 실점수를 사용한다")
    void PRIVATE_실점수_사용() {
        Resumes resume = Resumes.builder().id(10L).isActive(true).build();
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(resume));

        JobCandidate prv = candidate(100L, "PRIVATE");
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(prv));

        PrivateJobPosting posting = PrivateJobPosting.builder()
                .id(100L).company("test").sourceJobId("j1").build();
        PrivateMatchScore score = PrivateMatchScore.builder()
                .resume(resume).privateJobPosting(posting).score(92).build();
        when(privateMatchScoreRepository.findByResumeIdAndPrivateJobPostingIdIn(10L, List.of(100L)))
                .thenReturn(List.of(score));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PRIVATE"), null, null, 0, 10);

        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).matchScore()).isEqualTo(92);
    }

    @Test
    @DisplayName("실점수가 없는 PRIVATE 공고는 mockScore 폴백을 사용한다")
    void PRIVATE_점수미계산_mockScore폴백() {
        Resumes resume = Resumes.builder().id(10L).isActive(true).build();
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(resume));

        JobCandidate prv = candidate(100L, "PRIVATE");
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(prv));

        when(privateMatchScoreRepository.findByResumeIdAndPrivateJobPostingIdIn(10L, List.of(100L)))
                .thenReturn(List.of());

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PRIVATE"), null, null, 0, 10);

        int expectedMock = jobMatchScorer.mockScore("PRIVATE", 100L);
        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).matchScore()).isEqualTo(expectedMock);
    }

    @Test
    @DisplayName("활성 이력서가 있으면 PUBLIC 공고에 AI 실점수를 사용한다")
    void PUBLIC_실점수_사용() {
        Resumes resume = Resumes.builder().id(10L).isActive(true).build();
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(resume));

        JobCandidate pub = candidate(1L, "PUBLIC");
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of(pub));

        PublicJobPosting posting = PublicJobPosting.builder().id(1L).build();
        PublicMatchScore score = PublicMatchScore.builder()
                .resume(resume).publicJobPosting(posting).score(88).build();
        when(publicMatchScoreRepository.findByResumeIdAndPublicJobPostingIdIn(10L, List.of(1L)))
                .thenReturn(List.of(score));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PUBLIC"), null, null, 0, 10);

        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).matchScore()).isEqualTo(88);
    }

    @Test
    @DisplayName("실점수가 없는 PUBLIC 공고는 mockScore 폴백을 사용한다")
    void PUBLIC_점수미계산_mockScore폴백() {
        Resumes resume = Resumes.builder().id(10L).isActive(true).build();
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(resume));

        JobCandidate pub = candidate(1L, "PUBLIC");
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of(pub));

        when(publicMatchScoreRepository.findByResumeIdAndPublicJobPostingIdIn(10L, List.of(1L)))
                .thenReturn(List.of());

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PUBLIC"), null, null, 0, 10);

        int expectedMock = jobMatchScorer.mockScore("PUBLIC", 1L);
        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).matchScore()).isEqualTo(expectedMock);
    }

    @Test
    @DisplayName("전체 필터에서 PRIVATE은 실점수, PUBLIC은 mockScore로 함께 정렬된다")
    void 전체필터_실점수와_mockScore_혼합정렬() {
        Resumes resume = Resumes.builder().id(10L).isActive(true).build();
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(resume));

        JobCandidate pub = candidate(1L, "PUBLIC");
        JobCandidate prv = candidate(100L, "PRIVATE");
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of(pub));
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(prv));

        PrivateJobPosting posting = PrivateJobPosting.builder()
                .id(100L).company("test").sourceJobId("j1").build();
        PrivateMatchScore score = PrivateMatchScore.builder()
                .resume(resume).privateJobPosting(posting).score(95).build();
        when(privateMatchScoreRepository.findByResumeIdAndPrivateJobPostingIdIn(10L, List.of(100L)))
                .thenReturn(List.of(score));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, null, null, null, 0, 10);

        assertThat(response.jobs()).hasSize(2);
        assertThat(response.jobs()).isSortedAccordingTo(
                Comparator.comparingInt(RecommendedJob::matchScore).reversed());
    }

    // ── 이력서 미업로드 + 희망직무/지역 필터링 테스트 ──

    @Test
    @DisplayName("이력서 없음 + 희망직무만 설정 → 해당 직무 공고만 최신순, score null")
    void 이력서없음_희망직무필터() {
        Member member = Member.builder().id(1L).email(EMAIL).onboardingCompleted(true).build();
        member.getPrefJobs().add(new PreferredJob(member, "백엔드"));
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.empty());

        JobCandidate backend = candidateWithCategory(1L, "PRIVATE", "백엔드", LocalDateTime.of(2026, 6, 1, 0, 0));
        JobCandidate frontend = candidateWithCategory(2L, "PRIVATE", "프론트엔드", LocalDateTime.of(2026, 7, 1, 0, 0));
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(backend, frontend));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PRIVATE"), null, null, 0, 10);

        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).id()).isEqualTo(1L);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isNull());
    }

    @Test
    @DisplayName("이력서 없음 + 희망지역만 설정 → 해당 지역 공고만 최신순, score null")
    void 이력서없음_희망지역필터() {
        Member member = Member.builder().id(1L).email(EMAIL).onboardingCompleted(true).build();
        member.getPrefLocations().add(new PreferredRegion(member, "판교"));
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.empty());

        JobCandidate pangyo = new JobCandidate(1L, "PRIVATE", "카카오", "개발자", "판교", "신입", "백엔드", null, LocalDateTime.of(2026, 6, 1, 0, 0));
        JobCandidate seoul = new JobCandidate(2L, "PRIVATE", "네이버", "개발자", "서울", "신입", "백엔드", null, LocalDateTime.of(2026, 7, 1, 0, 0));
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(pangyo, seoul));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PRIVATE"), null, null, 0, 10);

        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).id()).isEqualTo(1L);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isNull());
    }

    @Test
    @DisplayName("이력서 없음 + 희망직무/지역 둘 다 설정 → 둘 다 필터링되어 최신순, score null")
    void 이력서없음_희망직무_지역_둘다필터() {
        Member member = Member.builder().id(1L).email(EMAIL).onboardingCompleted(true).build();
        member.getPrefJobs().add(new PreferredJob(member, "백엔드"));
        member.getPrefLocations().add(new PreferredRegion(member, "서울"));
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.empty());

        JobCandidate match = new JobCandidate(1L, "PRIVATE", "회사A", "개발자", "서울", "신입", "백엔드", null, LocalDateTime.of(2026, 6, 1, 0, 0));
        JobCandidate wrongCategory = new JobCandidate(2L, "PRIVATE", "회사B", "디자이너", "서울", "신입", "프론트엔드", null, LocalDateTime.of(2026, 7, 1, 0, 0));
        JobCandidate wrongLocation = new JobCandidate(3L, "PRIVATE", "회사C", "개발자", "부산", "신입", "백엔드", null, LocalDateTime.of(2026, 7, 1, 0, 0));
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(match, wrongCategory, wrongLocation));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PRIVATE"), null, null, 0, 10);

        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).id()).isEqualTo(1L);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isNull());
    }

    @Test
    @DisplayName("이력서 없음 + PUBLIC 공고도 jobCategory(jobRole) 기준으로 필터링된다")
    void 이력서없음_PUBLIC_직무필터() {
        Member member = Member.builder().id(1L).email(EMAIL).onboardingCompleted(true).build();
        member.getPrefJobs().add(new PreferredJob(member, "사무"));
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.empty());

        JobCandidate match = new JobCandidate(1L, "PUBLIC", "한국전력공사", "행정직", "서울", "신입", "사무행정", null, LocalDateTime.of(2026, 6, 1, 0, 0));
        JobCandidate noMatch = new JobCandidate(2L, "PUBLIC", "한국가스공사", "기술직", "서울", "신입", "전기전자", null, LocalDateTime.of(2026, 7, 1, 0, 0));
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of(match, noMatch));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PUBLIC"), null, null, 0, 10);

        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).id()).isEqualTo(1L);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isNull());
    }

    @Test
    @DisplayName("이력서 있으면 희망직무/지역 필터 없이 점수순으로 반환한다")
    void 이력서있으면_점수순_필터없음() {
        // setUp 기본값: 활성 이력서 있음 + 희망직무 "백엔드"
        JobCandidate backend = candidateWithCategory(1L, "PRIVATE", "백엔드", LocalDateTime.of(2026, 6, 1, 0, 0));
        JobCandidate frontend = candidateWithCategory(2L, "PRIVATE", "프론트엔드", LocalDateTime.of(2026, 7, 1, 0, 0));
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(backend, frontend));

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, List.of("PRIVATE"), null, null, 0, 10);

        // 이력서가 있으면 직무 필터 없이 모든 공고가 점수순으로 나옴
        assertThat(response.jobs()).hasSize(2);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isNotNull());
    }

    private static JobCandidate candidate(Long id, String source) {
        return new JobCandidate(id, source, "회사" + id, "제목" + id, "서울", "신입",
                "백엔드", null, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private static JobCandidate candidateWithCreatedAt(Long id, String source, LocalDateTime createdAt) {
        return new JobCandidate(id, source, "회사" + id, "제목" + id, "서울", "신입", "백엔드", null, createdAt);
    }

    private static JobCandidate candidateWithCategory(Long id, String source, String jobCategory, LocalDateTime createdAt) {
        return new JobCandidate(id, source, "회사" + id, "제목" + id, "서울", "신입", jobCategory, null, createdAt);
    }
}

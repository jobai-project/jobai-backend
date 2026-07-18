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
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HomeRecommendationServiceTest {

    private static final String EMAIL = "test@jobai.com";

    private MemberRepository memberRepository;
    private HomeJobCandidateRepository candidateRepository;
    private NotificationRepository notificationRepository;
    private ResumesRepository resumesRepository;
    private HomeRecommendationService service;

    @BeforeEach
    void setUp() {
        memberRepository = Mockito.mock(MemberRepository.class);
        candidateRepository = Mockito.mock(HomeJobCandidateRepository.class);
        notificationRepository = Mockito.mock(NotificationRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        service = new HomeRecommendationService(
                memberRepository, candidateRepository, notificationRepository, resumesRepository
        );

        Member member = memberWithPreferences();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL))
                .thenReturn(Optional.of(Resumes.builder().id(10L).isActive(true).build()));
        when(notificationRepository.findByMemberEmail(EMAIL))
                .thenReturn(Optional.of(Notification.builder().matchScoreThreshold(70).build()));
        when(candidateRepository.findScoredPublicCandidates(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(candidateRepository.findScoredPrivateCandidates(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of());
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of());
        when(candidateRepository.findLatestPublicCandidates(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        when(candidateRepository.findLatestPrivateCandidates(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 후보 조회를 하지 않는다")
    void memberNotFound() {
        when(memberRepository.findByEmail("ghost@jobai.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecommendedJobs("ghost@jobai.com", null, null, null, 0, 18))
                .isInstanceOf(GeneralException.class);

        verifyNoInteractions(candidateRepository);
    }

    @Test
    @DisplayName("저장된 공기업과 민간 매칭점수를 하나의 점수순 목록으로 병합한다")
    void mergesPersistedScores() {
        when(candidateRepository.findScoredPublicCandidates(10L, null, null, 70, 10))
                .thenReturn(List.of(scored(1L, "PUBLIC", 91), scored(2L, "PUBLIC", 75)));
        when(candidateRepository.findScoredPrivateCandidates(10L, null, null, 70, 10))
                .thenReturn(List.of(scored(10L, "PRIVATE", 95), scored(20L, "PRIVATE", 80)));
        when(candidateRepository.countScoredPublicCandidates(10L, null, null, 70)).thenReturn(2L);
        when(candidateRepository.countScoredPrivateCandidates(10L, null, null, 70)).thenReturn(2L);

        HomeRecommendationResponse response = service.getRecommendedJobs(EMAIL, null, null, null, 0, 10);

        assertThat(response.jobs()).extracting(RecommendedJob::matchScore)
                .containsExactly(95, 91, 80, 75);
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    @DisplayName("점수가 저장되지 않은 공고는 mock 점수 없이 추천 결과에서 제외한다")
    void excludesPostingWithoutPersistedScore() {
        HomeRecommendationResponse response = service.getRecommendedJobs(
                EMAIL, List.of("PUBLIC"), null, null, 0, 18
        );

        assertThat(response.jobs()).isEmpty();
        assertThat(response.totalCount()).isZero();
        verify(candidateRepository).findScoredPublicCandidates(10L, null, null, 70, 18);
        verify(candidateRepository, never())
                .findLatestPublicCandidates(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("점수 추천은 최근 공고 1000건 조회를 거치지 않고 저장 점수에서 직접 페이지를 조회한다")
    void scoredRecommendationDoesNotUseCandidateCap() {
        when(candidateRepository.findScoredPublicCandidates(10L, null, null, 70, 1_018))
                .thenReturn(List.of(scored(5L, "PUBLIC", 99)));
        when(candidateRepository.countScoredPublicCandidates(10L, null, null, 70)).thenReturn(2_000L);

        HomeRecommendationResponse response = service.getRecommendedJobs(
                EMAIL, List.of("PUBLIC"), null, null, 1_000, 18
        );

        verify(candidateRepository).findScoredPublicCandidates(10L, null, null, 70, 1_018);
        verify(candidateRepository, never())
                .findLatestPublicCandidates(any(), any(), any(), any(), anyInt());
        assertThat(response.totalCount()).isEqualTo(2_000);
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    @DisplayName("companyTypes가 PUBLIC이면 민간 점수 조회를 하지 않는다")
    void publicOnly() {
        service.getRecommendedJobs(EMAIL, List.of("PUBLIC"), null, null, 0, 18);

        verify(candidateRepository).findScoredPublicCandidates(10L, null, null, 70, 18);
        verify(candidateRepository, never())
                .findScoredPrivateCandidates(any(), any(), any(), anyInt(), anyInt());
        verify(candidateRepository, never())
                .countScoredPrivateCandidates(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("알림 설정이 없으면 실제 점수 조회에 기본 임계값 70을 사용한다")
    void defaultThreshold() {
        when(notificationRepository.findByMemberEmail(EMAIL)).thenReturn(Optional.empty());

        service.getRecommendedJobs(EMAIL, List.of("PRIVATE"), List.of("서울"), List.of("신입"), 0, 20);

        verify(candidateRepository).findScoredPrivateCandidates(
                eq(10L), eq(List.of("서울")), eq(List.of("신입")), eq(70), eq(20));
        verify(candidateRepository).countScoredPrivateCandidates(
                eq(10L), eq(List.of("서울")), eq(List.of("신입")), eq(70));
    }

    @Test
    @DisplayName("활성 이력서가 없으면 희망 조건으로 필터링하고 점수 없이 최신순 반환한다")
    void filtersLatestWithoutActiveResume() {
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.empty());
        Member member = memberWithPreferences();
        member.getPrefLocations().add(new PreferredRegion(member, "서울"));
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));

        JobCandidate match = candidate(1L, "PRIVATE", "백엔드", "서울", LocalDateTime.of(2026, 6, 1, 0, 0));
        when(candidateRepository.findLatestPrivateCandidates(
                null, null, List.of("백엔드"), List.of("서울"), 10))
                .thenReturn(List.of(match));
        when(candidateRepository.countLatestPrivateCandidates(
                null, null, List.of("백엔드"), List.of("서울"))).thenReturn(1L);

        HomeRecommendationResponse response = service.getRecommendedJobs(
                EMAIL, List.of("PRIVATE"), null, null, 0, 10
        );

        assertThat(response.jobs()).extracting(RecommendedJob::id).containsExactly(1L);
        assertThat(response.jobs().get(0).matchScore()).isNull();
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("희망 조건이 없는 회원은 활성 이력서가 있어도 최신순으로 반환한다")
    void latestWithoutPreferences() {
        Member member = Member.builder().id(1L).email(EMAIL).onboardingCompleted(true).build();
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        JobCandidate older = candidate(1L, "PUBLIC", "백엔드", "서울", LocalDateTime.of(2026, 1, 1, 0, 0));
        JobCandidate newer = candidate(2L, "PUBLIC", "백엔드", "서울", LocalDateTime.of(2026, 7, 1, 0, 0));
        when(candidateRepository.findLatestPublicCandidates(null, null, List.of(), List.of(), 10))
                .thenReturn(List.of(older, newer));
        when(candidateRepository.countLatestPublicCandidates(null, null, List.of(), List.of()))
                .thenReturn(2L);

        HomeRecommendationResponse response = service.getRecommendedJobs(
                EMAIL, List.of("PUBLIC"), null, null, 0, 10
        );

        assertThat(response.jobs()).extracting(RecommendedJob::id).containsExactly(2L, 1L);
        assertThat(response.jobs()).allSatisfy(job -> assertThat(job.matchScore()).isNull());
        verify(resumesRepository, never()).findByMemberEmailAndIsActiveTrue(EMAIL);
    }

    @ParameterizedTest
    @CsvSource({"-1,18", "0,0", "0,-1", "0,101"})
    @DisplayName("잘못된 offset 또는 size는 후보 조회 전에 거부한다")
    void rejectsInvalidPagination(int offset, int size) {
        assertThatThrownBy(() -> service.getRecommendedJobs(
                EMAIL, List.of("PUBLIC"), null, null, offset, size))
                .isInstanceOf(GeneralException.class)
                .satisfies(error -> assertThat(((GeneralException) error).getErrorCode().getCode())
                        .isEqualTo("COMMON_400_001"));

        verifyNoInteractions(candidateRepository);
    }

    @Test
    @DisplayName("동점 공고를 여러 페이지로 조회해도 중복과 누락 없이 동일한 순서를 유지한다")
    void paginatesTiedScoresDeterministically() {
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 7, 18, 0, 0);
        List<ScoredJobCandidate> publicScores = List.of(
                scored(1L, "PUBLIC", 90, sameCreatedAt),
                scored(2L, "PUBLIC", 90, sameCreatedAt)
        );
        List<ScoredJobCandidate> privateScores = List.of(
                scored(1L, "PRIVATE", 90, sameCreatedAt),
                scored(2L, "PRIVATE", 90, sameCreatedAt)
        );
        when(candidateRepository.findScoredPublicCandidates(eq(10L), any(), any(), eq(70), anyInt()))
                .thenReturn(publicScores);
        when(candidateRepository.findScoredPrivateCandidates(eq(10L), any(), any(), eq(70), anyInt()))
                .thenReturn(privateScores);
        when(candidateRepository.countScoredPublicCandidates(10L, null, null, 70)).thenReturn(2L);
        when(candidateRepository.countScoredPrivateCandidates(10L, null, null, 70)).thenReturn(2L);

        HomeRecommendationResponse first = service.getRecommendedJobs(EMAIL, null, null, null, 0, 2);
        HomeRecommendationResponse second = service.getRecommendedJobs(EMAIL, null, null, null, 2, 2);

        List<String> firstKeys = first.jobs().stream().map(job -> job.source() + ":" + job.id()).toList();
        List<String> secondKeys = second.jobs().stream().map(job -> job.source() + ":" + job.id()).toList();
        assertThat(firstKeys).doesNotContainAnyElementsOf(secondKeys);
        assertThat(java.util.stream.Stream.concat(firstKeys.stream(), secondKeys.stream()).toList())
                .containsExactly("PRIVATE:2", "PRIVATE:1", "PUBLIC:2", "PUBLIC:1");
    }

    private Member memberWithPreferences() {
        Member member = Member.builder().id(1L).email(EMAIL).onboardingCompleted(true).build();
        member.getPrefJobs().add(new PreferredJob(member, "백엔드"));
        return member;
    }

    private ScoredJobCandidate scored(Long id, String source, int score) {
        return scored(id, source, score, LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private ScoredJobCandidate scored(Long id, String source, int score, LocalDateTime createdAt) {
        return new ScoredJobCandidate(
                candidate(id, source, "백엔드", "서울", createdAt),
                score
        );
    }

    private JobCandidate candidate(
            Long id, String source, String jobCategory, String location, LocalDateTime createdAt
    ) {
        return new JobCandidate(
                id, source, "회사" + id, "제목" + id, location, "신입", jobCategory, null, createdAt
        );
    }
}

package com.jobai.backend.domain.scrap.service;

import com.jobai.backend.domain.jobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.jobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.entity.PublicMatchScore;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.domain.scrap.dto.ScrapResponseDTO;
import com.jobai.backend.domain.scrap.dto.ScrapResponseDTO.AddResultDTO;
import com.jobai.backend.domain.scrap.entity.MemberScrapHistory;
import com.jobai.backend.domain.scrap.repository.MemberScrapHistoryRepository;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScrapServiceTest {

    private static final String EMAIL = "test@jobai.com";

    private MemberRepository memberRepository;
    private MemberScrapHistoryRepository scrapHistoryRepository;
    private JobPostingRepository jobPostingRepository;
    private PrivateJobPostingRepository privateJobPostingRepository;
    private HomeJobCandidateRepository candidateRepository;
    private ResumesRepository resumesRepository;
    private PrivateMatchScoreRepository privateMatchScoreRepository;
    private PublicMatchScoreRepository publicMatchScoreRepository;
    private ScrapService service;

    @BeforeEach
    void setUp() {
        memberRepository = Mockito.mock(MemberRepository.class);
        scrapHistoryRepository = Mockito.mock(MemberScrapHistoryRepository.class);
        jobPostingRepository = Mockito.mock(JobPostingRepository.class);
        privateJobPostingRepository = Mockito.mock(PrivateJobPostingRepository.class);
        candidateRepository = Mockito.mock(HomeJobCandidateRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        privateMatchScoreRepository = Mockito.mock(PrivateMatchScoreRepository.class);
        publicMatchScoreRepository = Mockito.mock(PublicMatchScoreRepository.class);
        service = new ScrapService(memberRepository, scrapHistoryRepository, jobPostingRepository,
                privateJobPostingRepository, candidateRepository, resumesRepository,
                privateMatchScoreRepository, publicMatchScoreRepository);

        when(memberRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(Member.builder().id(1L).email(EMAIL).build()));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예외를 던진다")
    void 회원없으면_예외() {
        when(memberRepository.findByEmail("ghost@jobai.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addScrap("ghost@jobai.com", "PUBLIC", 1L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("source가 PUBLIC/PRIVATE가 아니면 예외를 던진다")
    void 잘못된_source면_예외() {
        assertThatThrownBy(() -> service.addScrap(EMAIL, "INVALID", 1L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("존재하지 않는 공고를 스크랩하려 하면 예외를 던진다")
    void 존재하지않는_공고면_예외() {
        when(jobPostingRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.addScrap(EMAIL, "PUBLIC", 999L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("공기업 공고를 정상적으로 스크랩한다")
    void 공기업_스크랩_성공() {
        when(jobPostingRepository.existsById(823L)).thenReturn(true);
        when(scrapHistoryRepository.findByMemberEmailAndSourceAndSourceId(EMAIL, "PUBLIC", 823L))
                .thenReturn(Optional.empty());
        when(scrapHistoryRepository.save(any(MemberScrapHistory.class)))
                .thenAnswer(inv -> {
                    MemberScrapHistory arg = inv.getArgument(0);
                    return MemberScrapHistory.builder().id(1L).member(arg.getMember())
                            .source(arg.getSource()).sourceId(arg.getSourceId()).build();
                });

        AddResultDTO result = service.addScrap(EMAIL, "public", 823L);

        assertThat(result.getScrapId()).isEqualTo(1L);
        verify(privateJobPostingRepository, never()).existsById(anyLong());
    }

    @Test
    @DisplayName("이미 스크랩한 공고를 다시 스크랩해도 기존 기록을 그대로 반환한다(멱등)")
    void 이미_스크랩한_공고는_멱등() {
        when(jobPostingRepository.existsById(823L)).thenReturn(true);
        Member member = Member.builder().id(1L).email(EMAIL).build();
        when(scrapHistoryRepository.findByMemberEmailAndSourceAndSourceId(EMAIL, "PUBLIC", 823L))
                .thenReturn(Optional.of(MemberScrapHistory.builder().id(5L).member(member).source("PUBLIC").sourceId(823L).build()));

        AddResultDTO result = service.addScrap(EMAIL, "PUBLIC", 823L);

        assertThat(result.getScrapId()).isEqualTo(5L);
        verify(scrapHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("사기업 공고 스크랩 시 사기업 리포지토리로 존재 여부를 확인한다")
    void 사기업_스크랩시_사기업리포지토리_확인() {
        when(privateJobPostingRepository.existsById(55L)).thenReturn(true);
        when(scrapHistoryRepository.findByMemberEmailAndSourceAndSourceId(EMAIL, "PRIVATE", 55L))
                .thenReturn(Optional.empty());
        when(scrapHistoryRepository.save(any(MemberScrapHistory.class)))
                .thenAnswer(inv -> {
                    MemberScrapHistory arg = inv.getArgument(0);
                    return MemberScrapHistory.builder().id(2L).member(arg.getMember())
                            .source(arg.getSource()).sourceId(arg.getSourceId()).build();
                });

        service.addScrap(EMAIL, "PRIVATE", 55L);

        verify(jobPostingRepository, never()).existsById(anyLong());
        verify(privateJobPostingRepository).existsById(55L);
    }

    @Test
    @DisplayName("스크랩 취소는 리포지토리 삭제 메서드를 호출한다")
    void 스크랩_취소() {
        service.removeScrap(EMAIL, "public", 823L);

        verify(scrapHistoryRepository).deleteByMemberEmailAndSourceAndSourceId(EMAIL, "PUBLIC", 823L);
    }

    @Test
    @DisplayName("내 스크랩 목록은 공기업/사기업 공고 정보를 채워 최근 스크랩순으로 반환한다")
    void 스크랩목록_조회() {
        Member member = Member.builder().id(1L).email(EMAIL).build();
        MemberScrapHistory older = MemberScrapHistory.builder().id(1L).member(member).source("PUBLIC").sourceId(823L)
                .scrappedAt(LocalDateTime.of(2026, 7, 4, 9, 0)).build();
        MemberScrapHistory newer = MemberScrapHistory.builder().id(2L).member(member).source("PRIVATE").sourceId(55L)
                .scrappedAt(LocalDateTime.of(2026, 7, 5, 9, 0)).build();

        when(scrapHistoryRepository.findByMemberEmailOrderByScrappedAtDesc(EMAIL))
                .thenReturn(List.of(newer, older));

        JobCandidate publicCandidate = new JobCandidate(823L, "PUBLIC", "한국전력공사", "채용공고",
                "서울", "정규직", null, null, LocalDateTime.now());
        JobCandidate privateCandidate = new JobCandidate(55L, "PRIVATE", "카카오", "백엔드 개발자",
                "판교", "경력", "백엔드", null, LocalDateTime.now());

        when(candidateRepository.findPublicCandidatesByIds(List.of(823L))).thenReturn(List.of(publicCandidate));
        when(candidateRepository.findPrivateCandidatesByIds(List.of(55L))).thenReturn(List.of(privateCandidate));

        Resumes activeResume = Resumes.builder().id(10L).member(member).isActive(true).build();
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(activeResume));
        when(publicMatchScoreRepository.findByResumeIdAndPublicJobPostingIdIn(10L, List.of(823L)))
                .thenReturn(List.of(PublicMatchScore.builder()
                        .member(member)
                        .resume(activeResume)
                        .publicJobPosting(PublicJobPosting.builder().id(823L).build())
                        .score(92)
                        .build()));
        when(privateMatchScoreRepository.findByResumeIdAndPrivateJobPostingIdIn(10L, List.of(55L)))
                .thenReturn(List.of(PrivateMatchScore.builder()
                        .member(member)
                        .resume(activeResume)
                        .privateJobPosting(PrivateJobPosting.builder().id(55L).build())
                        .score(88)
                        .build()));

        ScrapResponseDTO.ScrapListDTO result = service.getMyScraps(EMAIL);

        assertThat(result.getScraps()).hasSize(2);
        assertThat(result.getScraps().get(0).getSourceId()).isEqualTo(55L); // 최근 스크랩(newer)이 먼저
        assertThat(result.getScraps().get(0).getCompanyName()).isEqualTo("카카오");
        assertThat(result.getScraps().get(0).getMatchScore()).isEqualTo(88);
        assertThat(result.getScraps().get(1).getSourceId()).isEqualTo(823L);
        assertThat(result.getScraps().get(1).getCompanyName()).isEqualTo("한국전력공사");
        assertThat(result.getScraps().get(1).getMatchScore()).isEqualTo(92);
    }

    @Test
    @DisplayName("활성 이력서가 없으면 스크랩 매칭점수는 null이다")
    void 활성_이력서가_없으면_매칭점수는_null() {
        Member member = Member.builder().id(1L).email(EMAIL).build();
        MemberScrapHistory history = MemberScrapHistory.builder()
                .id(1L)
                .member(member)
                .source("PUBLIC")
                .sourceId(823L)
                .scrappedAt(LocalDateTime.now())
                .build();
        when(scrapHistoryRepository.findByMemberEmailOrderByScrappedAtDesc(EMAIL)).thenReturn(List.of(history));
        when(candidateRepository.findPublicCandidatesByIds(List.of(823L)))
                .thenReturn(List.of(candidate(823L, null)));

        ScrapResponseDTO.ScrapListDTO result = service.getMyScraps(EMAIL);

        assertThat(result.getScraps()).singleElement()
                .extracting(ScrapResponseDTO.ScrapItemDTO::getMatchScore)
                .isNull();
        verifyNoInteractions(privateMatchScoreRepository, publicMatchScoreRepository);
    }

    @Test
    @DisplayName("원본 공고가 삭제된 스크랩 기록은 목록에서 제외된다")
    void 원본삭제된_스크랩은_제외() {
        Member member = Member.builder().id(1L).email(EMAIL).build();
        MemberScrapHistory orphaned = MemberScrapHistory.builder().id(1L).member(member).source("PUBLIC").sourceId(999L)
                .scrappedAt(LocalDateTime.now()).build();

        when(scrapHistoryRepository.findByMemberEmailOrderByScrappedAtDesc(EMAIL)).thenReturn(List.of(orphaned));
        when(candidateRepository.findPublicCandidatesByIds(List.of(999L))).thenReturn(List.of());

        ScrapResponseDTO.ScrapListDTO result = service.getMyScraps(EMAIL);

        assertThat(result.getScraps()).isEmpty();
    }
    @Test
    @DisplayName("마감 임박 스크랩 공고는 최대 3개를 고정된 기준으로 정렬해 조회한다")
    void 마감_임박_스크랩_조회() {
        LocalDate today = LocalDate.now();
        when(scrapHistoryRepository.findUpcomingPublicDeadlineScraps(any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        upcomingRow(1L, "PUBLIC", 1L, today.plusDays(1), LocalDateTime.of(2026, 7, 1, 9, 0)),
                        upcomingRow(2L, "PUBLIC", 2L, today.plusDays(2), LocalDateTime.of(2026, 7, 3, 9, 0)),
                        upcomingRow(3L, "PUBLIC", 3L, today.plusDays(2), LocalDateTime.of(2026, 7, 2, 9, 0))
                ));
        when(scrapHistoryRepository.findUpcomingPrivateDeadlineScraps(any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        upcomingRow(4L, "PRIVATE", 4L, today.plusDays(3), LocalDateTime.of(2026, 7, 4, 9, 0))
                ));

        ScrapResponseDTO.ScrapListDTO result = service.getUpcomingDeadlineScraps(EMAIL);

        assertThat(result.getScraps()).extracting(ScrapResponseDTO.ScrapItemDTO::getSourceId)
                .containsExactly(1L, 2L, 3L);
    }

    private JobCandidate candidate(Long id, LocalDate deadline) {
        return new JobCandidate(id, "PUBLIC", "company" + id, "title" + id,
                "Seoul", "full-time", "backend", deadline, LocalDateTime.now());
    }

    private Object[] upcomingRow(Long scrapId, String source, Long sourceId, LocalDate deadline, LocalDateTime scrappedAt) {
        return new Object[]{
                scrapId,
                source,
                sourceId,
                "company" + sourceId,
                "title" + sourceId,
                "Seoul",
                "full-time",
                deadline,
                scrappedAt
        };
    }
}

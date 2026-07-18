package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.matching.entity.PublicMatchScore;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.publicInstitution.dto.PublicJobPostingDetailResponse;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicJobPostingServiceTest {

    private static final String EMAIL = "member@jobai.com";

    private JobPostingRepository jobPostingRepository;
    private ResumesRepository resumesRepository;
    private PublicMatchScoreRepository publicMatchScoreRepository;
    private PublicJobPostingService service;

    @BeforeEach
    void setUp() {
        jobPostingRepository = Mockito.mock(JobPostingRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        publicMatchScoreRepository = Mockito.mock(PublicMatchScoreRepository.class);
        service = new PublicJobPostingService(
                jobPostingRepository,
                resumesRepository,
                publicMatchScoreRepository
        );
    }

    @Test
    @DisplayName("활성 이력서에 저장된 공기업 매칭점수와 산정 사유를 상세 응답에 포함한다")
    void getDetailReturnsSavedMatchScore() {
        PublicJobPosting posting = createPosting(1L);
        Resumes activeResume = Resumes.builder().id(10L).isActive(true).build();
        PublicMatchScore savedScore = PublicMatchScore.builder()
                .resume(activeResume)
                .publicJobPosting(posting)
                .score(88)
                .scoreReason("직무 역량과 보유 기술이 높은 수준으로 일치합니다.")
                .build();

        when(jobPostingRepository.findPublicJobPostingById(1L)).thenReturn(Optional.of(posting));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(activeResume));
        when(publicMatchScoreRepository.findByResumeIdAndPublicJobPostingIdIn(10L, List.of(1L)))
                .thenReturn(List.of(savedScore));

        PublicJobPostingDetailResponse response = service.getDetail(1L, EMAIL);

        assertThat(response.matchScore()).isEqualTo(88);
        assertThat(response.scoreReason()).isEqualTo("직무 역량과 보유 기술이 높은 수준으로 일치합니다.");
    }

    @Test
    @DisplayName("활성 이력서가 없으면 매칭점수와 산정 사유를 null로 반환한다")
    void getDetailReturnsNullScoreWithoutActiveResume() {
        when(jobPostingRepository.findPublicJobPostingById(1L)).thenReturn(Optional.of(createPosting(1L)));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.empty());

        PublicJobPostingDetailResponse response = service.getDetail(1L, EMAIL);

        assertThat(response.matchScore()).isNull();
        assertThat(response.scoreReason()).isNull();
        verifyNoInteractions(publicMatchScoreRepository);
    }

    @Test
    @DisplayName("계산된 점수가 없으면 매칭점수와 산정 사유를 null로 반환한다")
    void getDetailReturnsNullScoreWithoutSavedScore() {
        Resumes activeResume = Resumes.builder().id(10L).isActive(true).build();
        when(jobPostingRepository.findPublicJobPostingById(1L)).thenReturn(Optional.of(createPosting(1L)));
        when(resumesRepository.findByMemberEmailAndIsActiveTrue(EMAIL)).thenReturn(Optional.of(activeResume));
        when(publicMatchScoreRepository.findByResumeIdAndPublicJobPostingIdIn(10L, List.of(1L)))
                .thenReturn(List.of());

        PublicJobPostingDetailResponse response = service.getDetail(1L, EMAIL);

        assertThat(response.matchScore()).isNull();
        assertThat(response.scoreReason()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 공기업 채용공고를 조회하면 NOT_FOUND 예외를 던진다")
    void getDetailThrowsNotFound() {
        when(jobPostingRepository.findPublicJobPostingById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(999L, EMAIL))
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> assertThat(((GeneralException) exception).getErrorCode().getCode())
                        .isEqualTo("COMMON_404_001"));
    }

    private PublicJobPosting createPosting(Long id) {
        return PublicJobPosting.builder()
                .id(id)
                .title("2026년도 공기업 신입 채용")
                .companyName("한국테스트공사")
                .companyType("공기업")
                .recrutType("정규직")
                .workExperience("신입")
                .workRegion("서울")
                .beginDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .jobRole("IT/정보통신")
                .applyQualification("학력 무관")
                .disqualificationReason("인사규정상 결격사유")
                .applicationMethod("온라인 접수")
                .applyLink("https://example.com/apply")
                .isClosed(false)
                .htmlContent("<p>채용 상세</p>")
                .build();
    }
}
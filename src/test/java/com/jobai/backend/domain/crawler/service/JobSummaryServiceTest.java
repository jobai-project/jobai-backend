package com.jobai.backend.domain.crawler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.dto.JobSummaryResponse;
import com.jobai.backend.domain.crawler.dto.PrivateJobDetailResponse;
import com.jobai.backend.domain.crawler.entity.JobPostingSummary;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.repository.JobPostingSummaryRepository;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.crawler.summary.JobSummarizer;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import com.jobai.backend.global.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JobSummaryServiceTest {

    private PrivateJobPostingRepository jobPostingRepository;
    private JobPostingSummaryRepository summaryRepository;
    private JobSummarizer jobSummarizer;
    private JobSummaryService service;

    private static final String VALID_SUMMARY_JSON = """
            {
              "techStack": ["Java", "Spring"],
              "responsibilities": ["백엔드 개발"],
              "qualifications": ["Java 3년"],
              "preferredQualifications": ["MSA 경험"]
            }
            """;

    @BeforeEach
    void setUp() {
        jobPostingRepository = Mockito.mock(PrivateJobPostingRepository.class);
        summaryRepository = Mockito.mock(JobPostingSummaryRepository.class);
        jobSummarizer = Mockito.mock(JobSummarizer.class);
        service = new JobSummaryService(
                jobPostingRepository, summaryRepository, jobSummarizer, new ObjectMapper(),
                Mockito.mock(ResumesRepository.class), Mockito.mock(PrivateMatchScoreRepository.class));
    }

    private PrivateJobPosting createPosting(Long id, String description, LocalDateTime updatedAt) {
        return PrivateJobPosting.builder()
                .id(id)
                .title("백엔드 개발자")
                .company("테스트회사")
                .location("서울")
                .employmentType("정규직")
                .jobCategory("백엔드")
                .description(description)
                .applyUrl("https://example.com/apply")
                .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0))
                .updatedAt(updatedAt)
                .lastSeenAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("상세 조회: 요약 캐시 없으면 summary null로 반환한다")
    void getDetailReturnsResponseWithoutSummary() {
        PrivateJobPosting posting = createPosting(1L, "상세 설명", null);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.empty());

        PrivateJobDetailResponse result = service.getDetail(1L, null);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("백엔드 개발자");
        assertThat(result.getDescription()).isEqualTo("상세 설명");
        assertThat(result.getSummary()).isNull();
    }

    @Test
    @DisplayName("상세 조회: 요약 캐시가 있으면 summary를 함께 반환한다")
    void getDetailReturnsResponseWithSummary() {
        PrivateJobPosting posting = createPosting(1L, "상세 설명",
                LocalDateTime.of(2025, 1, 1, 12, 0));
        JobPostingSummary cached = JobPostingSummary.builder()
                .jobPostingId(1L)
                .summaryJson(VALID_SUMMARY_JSON)
                .sourceUpdatedAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build();

        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.of(cached));

        PrivateJobDetailResponse result = service.getDetail(1L, null);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSummary()).isNotNull();
        assertThat(result.getSummary().getTechStack()).contains("Java", "Spring");
    }

    @Test
    @DisplayName("상세 조회: 공고가 없으면 NOT_FOUND 예외")
    void getDetailThrowsNotFound() {
        when(jobPostingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(999L, null))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("COMMON_404_001"));
    }

    @Test
    @DisplayName("요약: 캐시가 있으면 LLM을 호출하지 않고 캐시를 반환한다")
    void getSummaryReturnsCached() {
        LocalDateTime updatedAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        PrivateJobPosting posting = createPosting(1L,
                "이 공고는 충분히 긴 설명을 포함하고 있습니다. 자바 백엔드 개발자를 모집합니다.", updatedAt);
        JobPostingSummary cached = JobPostingSummary.builder()
                .jobPostingId(1L)
                .summaryJson(VALID_SUMMARY_JSON)
                .sourceUpdatedAt(updatedAt)
                .build();

        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.of(cached));

        JobSummaryResponse result = service.getSummary(1L);

        assertThat(result.getJobPostingId()).isEqualTo(1L);
        assertThat(result.getSummary().getTechStack()).contains("Java", "Spring");
        verifyNoInteractions(jobSummarizer);
    }

    @Test
    @DisplayName("요약: 캐시가 없으면 LLM 호출 후 저장하고 반환한다")
    void getSummaryGeneratesNewSummary() {
        LocalDateTime updatedAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        PrivateJobPosting posting = createPosting(1L,
                "이 공고는 충분히 긴 설명을 포함하고 있습니다. 자바 백엔드 개발자를 모집합니다.", updatedAt);

        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.empty());
        when(jobSummarizer.summarize(anyString())).thenReturn(VALID_SUMMARY_JSON);

        JobSummaryResponse result = service.getSummary(1L);

        assertThat(result.getSummary().getResponsibilities()).contains("백엔드 개발");
        verify(summaryRepository).save(any(JobPostingSummary.class));
    }

    @Test
    @DisplayName("요약: 소스가 업데이트되면 캐시를 갱신한다")
    void getSummaryRegeneratesOnSourceUpdate() {
        LocalDateTime oldTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime newTime = LocalDateTime.of(2025, 6, 1, 12, 0);
        PrivateJobPosting posting = createPosting(1L,
                "이 공고는 충분히 긴 설명을 포함하고 있습니다. 업데이트된 백엔드 개발자 모집 공고.", newTime);
        JobPostingSummary cached = JobPostingSummary.builder()
                .jobPostingId(1L)
                .summaryJson(VALID_SUMMARY_JSON)
                .sourceUpdatedAt(oldTime)
                .build();

        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.of(cached));
        when(jobSummarizer.summarize(anyString())).thenReturn(VALID_SUMMARY_JSON);

        service.getSummary(1L);

        verify(jobSummarizer).summarize(anyString());
    }

    @Test
    @DisplayName("요약: 공고가 없으면 NOT_FOUND 예외")
    void getSummaryThrowsNotFound() {
        when(jobPostingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary(999L))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("COMMON_404_001"));
    }

    @Test
    @DisplayName("요약: description이 30자 미만이면 BAD_REQUEST 예외")
    void getSummaryThrowsBadRequestForShortDescription() {
        PrivateJobPosting posting = createPosting(1L, "짧은 설명", null);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.getSummary(1L))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("COMMON_400_001"));
    }

    @Test
    @DisplayName("요약: description이 null이면 BAD_REQUEST 예외")
    void getSummaryThrowsBadRequestForNullDescription() {
        PrivateJobPosting posting = createPosting(1L, null, null);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));

        assertThatThrownBy(() -> service.getSummary(1L))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("COMMON_400_001"));
    }

    @Test
    @DisplayName("요약: LLM 실패 시 SUMMARY_GENERATION_FAILED(503) 예외")
    void getSummaryThrows503OnLlmFailure() {
        LocalDateTime updatedAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        PrivateJobPosting posting = createPosting(1L,
                "이 공고는 충분히 긴 설명을 포함하고 있습니다. 자바 백엔드 개발자를 모집합니다.", updatedAt);

        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.empty());
        when(jobSummarizer.summarize(anyString()))
                .thenThrow(new LlmException("Anthropic 호출 실패"));

        assertThatThrownBy(() -> service.getSummary(1L))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("SUMMARY_503_001"));
    }
}

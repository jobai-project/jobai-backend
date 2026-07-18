package com.jobai.backend.domain.crawler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.privatejob.entity.JobPostingSummary;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejob.repository.JobPostingSummaryRepository;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.privatejob.service.PrivateJobDetailService;
import com.jobai.backend.domain.privatejob.service.JobSummarizer;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.privatejob.controller.PrivateJobController;
import com.jobai.backend.global.apiPayload.handler.GeneralExceptionAdvice;
import com.jobai.backend.global.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrivateJobControllerTest {

    private MockMvc mockMvc;
    private PrivateJobPostingRepository jobPostingRepository;
    private JobPostingSummaryRepository summaryRepository;
    private JobSummarizer jobSummarizer;
    private ResumesRepository resumesRepository;
    private PrivateMatchScoreRepository privateMatchScoreRepository;

    private static final String VALID_SUMMARY_JSON = """
            {"techStack":["Java","Spring Boot"],"responsibilities":["백엔드 API 개발"],"qualifications":["Java 3년 이상"],"preferredQualifications":["MSA 경험"]}""";

    @BeforeEach
    void setUp() {
        jobPostingRepository = Mockito.mock(PrivateJobPostingRepository.class);
        summaryRepository = Mockito.mock(JobPostingSummaryRepository.class);
        jobSummarizer = Mockito.mock(JobSummarizer.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        privateMatchScoreRepository = Mockito.mock(PrivateMatchScoreRepository.class);

        ObjectMapper objectMapper = new ObjectMapper();
        PrivateJobDetailService service = new PrivateJobDetailService(
                jobPostingRepository, summaryRepository, jobSummarizer, objectMapper,
                resumesRepository, privateMatchScoreRepository);
        PrivateJobController controller = new PrivateJobController(service);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionAdvice())
                .build();
    }

    private PrivateJobPosting createPosting(Long id, String description) {
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
                .updatedAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .lastSeenAt(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------
    // GET /api/v1/private-jobs/{id} — 상세 조회
    // -------------------------------------------------------

    @Test
    @DisplayName("상세 조회: 존재하는 공고 (요약 없음) → 200 + 원문 description 포함, summary 미포함")
    void getDetail_success() throws Exception {
        PrivateJobPosting posting = createPosting(1L, "상세 공고 본문 텍스트");
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/private-jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.id").value(1))
                .andExpect(jsonPath("$.result.title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.result.company").value("테스트회사"))
                .andExpect(jsonPath("$.result.description").value("상세 공고 본문 텍스트"))
                .andExpect(jsonPath("$.result.applyUrl").value("https://example.com/apply"))
                .andExpect(jsonPath("$.result.summary").doesNotExist());
    }

    @Test
    @DisplayName("상세 조회: 캐시된 요약이 있으면 summary를 함께 반환한다")
    void getDetail_withCachedSummary() throws Exception {
        PrivateJobPosting posting = createPosting(1L, "상세 공고 본문 텍스트");
        JobPostingSummary cached = JobPostingSummary.builder()
                .jobPostingId(1L)
                .summaryJson(VALID_SUMMARY_JSON)
                .sourceUpdatedAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build();

        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.of(cached));

        mockMvc.perform(get("/api/v1/private-jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.description").value("상세 공고 본문 텍스트"))
                .andExpect(jsonPath("$.result.summary.techStack[0]").value("Java"))
                .andExpect(jsonPath("$.result.summary.responsibilities[0]").value("백엔드 API 개발"));
    }

    @Test
    @DisplayName("상세 조회: 없는 공고 → 404")
    void getDetail_notFound() throws Exception {
        when(jobPostingRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/private-jobs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_404_001"));
    }

    // -------------------------------------------------------
    // GET /api/v1/private-jobs/{id}/summary — 요약
    // -------------------------------------------------------

    @Test
    @DisplayName("요약: 첫 호출 → LLM 요약 생성 + 저장 + 구조화된 응답 반환")
    void getSummary_newGeneration() throws Exception {
        PrivateJobPosting posting = createPosting(1L,
                "이 공고는 충분히 긴 설명을 포함하고 있습니다. 자바 백엔드 개발자를 모집합니다.");
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.empty());
        when(jobSummarizer.summarize(anyString())).thenReturn(VALID_SUMMARY_JSON);

        mockMvc.perform(get("/api/v1/private-jobs/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.jobPostingId").value(1))
                .andExpect(jsonPath("$.result.title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.result.company").value("테스트회사"))
                .andExpect(jsonPath("$.result.summary.techStack", hasSize(2)))
                .andExpect(jsonPath("$.result.summary.techStack[0]").value("Java"))
                .andExpect(jsonPath("$.result.summary.responsibilities[0]").value("백엔드 API 개발"))
                .andExpect(jsonPath("$.result.summary.qualifications[0]").value("Java 3년 이상"))
                .andExpect(jsonPath("$.result.summary.preferredQualifications[0]").value("MSA 경험"));

        verify(summaryRepository).save(any(JobPostingSummary.class));
    }

    @Test
    @DisplayName("요약: 캐시 존재 시 → LLM 미호출 + 캐시 반환")
    void getSummary_cached() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        PrivateJobPosting posting = createPosting(1L,
                "이 공고는 충분히 긴 설명을 포함하고 있습니다. 자바 백엔드 개발자를 모집합니다.");
        JobPostingSummary cached = JobPostingSummary.builder()
                .jobPostingId(1L)
                .summaryJson(VALID_SUMMARY_JSON)
                .sourceUpdatedAt(updatedAt)
                .build();

        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.of(cached));

        mockMvc.perform(get("/api/v1/private-jobs/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summary.techStack[0]").value("Java"));

        verifyNoInteractions(jobSummarizer);
    }

    @Test
    @DisplayName("요약: 소스 업데이트 시 → LLM 재호출")
    void getSummary_staleCache() throws Exception {
        LocalDateTime oldTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        LocalDateTime newTime = LocalDateTime.of(2025, 6, 1, 12, 0);

        PrivateJobPosting posting = PrivateJobPosting.builder()
                .id(1L).title("백엔드 개발자").company("테스트회사")
                .description("이 공고는 충분히 긴 설명을 포함하고 있습니다. 업데이트된 공고입니다.")
                .applyUrl("https://example.com/apply")
                .createdAt(LocalDateTime.of(2025, 1, 1, 0, 0))
                .updatedAt(newTime)
                .lastSeenAt(LocalDateTime.now())
                .build();
        JobPostingSummary cached = JobPostingSummary.builder()
                .jobPostingId(1L)
                .summaryJson(VALID_SUMMARY_JSON)
                .sourceUpdatedAt(oldTime)
                .build();

        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.of(cached));
        when(jobSummarizer.summarize(anyString())).thenReturn(VALID_SUMMARY_JSON);

        mockMvc.perform(get("/api/v1/private-jobs/1/summary"))
                .andExpect(status().isOk());

        verify(jobSummarizer).summarize(anyString());
    }

    @Test
    @DisplayName("요약: 없는 공고 → 404")
    void getSummary_notFound() throws Exception {
        when(jobPostingRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/private-jobs/999/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_404_001"));
    }

    @Test
    @DisplayName("요약: description 짧음 → 400")
    void getSummary_shortDescription() throws Exception {
        PrivateJobPosting posting = createPosting(1L, "짧은 설명");
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));

        mockMvc.perform(get("/api/v1/private-jobs/1/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
    }

    @Test
    @DisplayName("요약: description null → 400")
    void getSummary_nullDescription() throws Exception {
        PrivateJobPosting posting = createPosting(1L, null);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));

        mockMvc.perform(get("/api/v1/private-jobs/1/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
    }

    @Test
    @DisplayName("요약: LLM 실패 → 503")
    void getSummary_llmFailure() throws Exception {
        PrivateJobPosting posting = createPosting(1L,
                "이 공고는 충분히 긴 설명을 포함하고 있습니다. 자바 백엔드 개발자를 모집합니다.");
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(summaryRepository.findByJobPostingId(1L)).thenReturn(Optional.empty());
        when(jobSummarizer.summarize(anyString()))
                .thenThrow(new LlmException("Anthropic 호출 실패: 503"));

        mockMvc.perform(get("/api/v1/private-jobs/1/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SUMMARY_503_001"));
    }
}

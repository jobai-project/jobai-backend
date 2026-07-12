package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.repository.JobSearchRepository;
import com.jobai.backend.domain.search.repository.VectorSearchRepository;
import com.jobai.backend.domain.search.repository.VectorSearchRepository.ScoredJob;
import com.jobai.backend.domain.search.service.KeywordMatcher.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobSearchServiceTest {

    private KeywordMatcher keywordMatcher;
    private JobSearchRepository jobSearchRepository;
    private EmbeddingService embeddingService;
    private VectorSearchRepository vectorSearchRepository;
    private JobSearchService jobSearchService;

    @BeforeEach
    void setUp() throws Exception {
        keywordMatcher = Mockito.mock(KeywordMatcher.class);
        jobSearchRepository = Mockito.mock(JobSearchRepository.class);
        embeddingService = Mockito.mock(EmbeddingService.class);
        vectorSearchRepository = Mockito.mock(VectorSearchRepository.class);

        jobSearchService = new JobSearchService(
                keywordMatcher, jobSearchRepository,
                embeddingService, vectorSearchRepository);

        var field = JobSearchService.class.getDeclaredField("embeddingEnabled");
        field.setAccessible(true);
        field.setBoolean(jobSearchService, true);
    }

    // --- 경로 A: 미매칭 토큰 없음 → 기존 검색 ---

    @Test
    @DisplayName("미매칭 토큰 없으면 기존 검색, 벡터 안 씀")
    void 미매칭없으면_기존검색() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, "서울", null, List.of(), List.of(), List.of()));

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.countPrivate(any())).thenReturn(0L);
        when(jobSearchRepository.countPublic(any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("서울 백엔드", 0, 20);

        assertThat(response.searchInfo().method()).isEqualTo("KEYWORD");
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorSearchRepository);
    }

    // --- 경로 B: 미매칭 토큰 있음 → 벡터 검색 ---

    @Test
    @DisplayName("미매칭 토큰 있으면 벡터 검색 실행")
    void 미매칭있으면_벡터검색() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, "서울", null, List.of(), List.of(), List.of("혼자", "일하기")));

        float[] queryVector = new float[]{0.1f, 0.2f};
        when(embeddingService.embedQuery(anyString())).thenReturn(queryVector);

        JobSummary job = createJobSummary(1L, "PRIVATE", "자율 근무 백엔드",
                LocalDateTime.of(2025, 6, 1, 10, 0));
        when(vectorSearchRepository.searchPrivateByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new ScoredJob(job, 0.15)));
        when(vectorSearchRepository.searchPublicByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vectorSearchRepository.countPrivateByVector(any(), anyDouble(), any())).thenReturn(1L);
        when(vectorSearchRepository.countPublicByVector(any(), anyDouble(), any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("서울에서 혼자 일하기 쉬운 백엔드", 0, 20);

        assertThat(response.searchInfo().method()).isEqualTo("VECTOR");
        assertThat(response.jobs()).hasSize(1);
        assertThat(response.searchInfo().matchedCategories()).contains("백엔드");
    }

    @Test
    @DisplayName("벡터 검색에 카테고리/지역/경력 pre-filter가 전달된다")
    void 벡터검색_필터전달() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, "판교", "경력", List.of("경력", "무관", "미확인"), List.of(), List.of("혼자")));

        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});
        when(vectorSearchRepository.searchPrivateByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vectorSearchRepository.searchPublicByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vectorSearchRepository.countPrivateByVector(any(), anyDouble(), any())).thenReturn(0L);
        when(vectorSearchRepository.countPublicByVector(any(), anyDouble(), any())).thenReturn(0L);

        jobSearchService.search("판교에서 혼자 일하기 좋은 경력직 백엔드", 0, 20);

        Mockito.verify(vectorSearchRepository).searchPrivateByVector(
                any(), anyDouble(),
                argThat(cond -> cond.categories().contains("백엔드")
                        && "판교".equals(cond.location())
                        && "경력".equals(cond.experience())),
                anyInt(), anyInt());
    }

    // --- 벡터 결과 0건 → 빈 결과 반환 ---

    @Test
    @DisplayName("벡터 검색 결과 0건이면 빈 결과 반환 (폴백 없음)")
    void 벡터검색_결과없으면_빈결과() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of(), null, null, null, List.of(), List.of(), List.of("혼자", "일하기")));

        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});
        when(vectorSearchRepository.searchPrivateByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vectorSearchRepository.searchPublicByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vectorSearchRepository.countPrivateByVector(any(), anyDouble(), any())).thenReturn(0L);
        when(vectorSearchRepository.countPublicByVector(any(), anyDouble(), any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("혼자 일하기 편한 직무", 0, 20);

        assertThat(response.searchInfo().method()).isEqualTo("VECTOR");
        assertThat(response.jobs()).isEmpty();
        assertThat(response.totalCount()).isZero();
        verifyNoInteractions(jobSearchRepository);
    }

    // --- ai-server 장애 → 기존 검색 폴백 ---

    @Test
    @DisplayName("임베딩 실패 시 기존 검색으로 폴백")
    void 임베딩실패시_폴백() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), List.of("혼자")));

        when(embeddingService.embedQuery(anyString()))
                .thenThrow(new RuntimeException("ai-server 연결 실패"));

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.countPrivate(any())).thenReturn(0L);
        when(jobSearchRepository.countPublic(any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("혼자 일하기 좋은 백엔드", 0, 20);

        assertThat(response.searchInfo().method()).isEqualTo("KEYWORD");
        verifyNoInteractions(vectorSearchRepository);
    }

    // --- 유사도순 정렬 ---

    @Test
    @DisplayName("사기업/공기업 유사도순(distance 오름차순) 합산 정렬")
    void 소스무관_유사도순정렬() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of(), null, null, null, List.of(), List.of(), List.of("혼자")));

        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});

        JobSummary prv1 = createJobSummary(1L, "PRIVATE", "사기업A", LocalDateTime.of(2025, 6, 5, 10, 0));
        JobSummary prv2 = createJobSummary(2L, "PRIVATE", "사기업B", LocalDateTime.of(2025, 6, 3, 10, 0));
        when(vectorSearchRepository.searchPrivateByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        new ScoredJob(prv1, 0.20),   // 유사도 2위
                        new ScoredJob(prv2, 0.35)    // 유사도 3위
                ));

        JobSummary pub1 = createJobSummary(101L, "PUBLIC", "공기업A", LocalDateTime.of(2025, 6, 4, 10, 0));
        when(vectorSearchRepository.searchPublicByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        new ScoredJob(pub1, 0.10)    // 유사도 1위
                ));

        when(vectorSearchRepository.countPrivateByVector(any(), anyDouble(), any())).thenReturn(2L);
        when(vectorSearchRepository.countPublicByVector(any(), anyDouble(), any())).thenReturn(1L);

        JobSearchResponse response = jobSearchService.search("혼자 일하기 편한", 0, 3);

        // distance 오름차순: 공기업A(0.10) → 사기업A(0.20) → 사기업B(0.35)
        assertThat(response.jobs().stream().map(JobSummary::title).toList())
                .containsExactly("공기업A", "사기업A", "사기업B");
    }

    // --- embeddingEnabled = false ---

    @Test
    @DisplayName("벡터 비활성화 시 미매칭 있어도 기존 검색")
    void 벡터비활성화() throws Exception {
        var field = JobSearchService.class.getDeclaredField("embeddingEnabled");
        field.setAccessible(true);
        field.setBoolean(jobSearchService, false);

        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of(), null, null, null, List.of(), List.of(), List.of("혼자")));

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.countPrivate(any())).thenReturn(0L);
        when(jobSearchRepository.countPublic(any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("혼자 일하기 편한 직무", 0, 20);

        assertThat(response.searchInfo().method()).isEqualTo("KEYWORD");
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorSearchRepository);
    }

    // --- 헬퍼 ---

    private static JobSummary createJobSummary(Long id, String source, String title,
                                                LocalDateTime createdAt) {
        return new JobSummary(id, source, title, "회사", "서울", "백엔드",
                "정규직", "신입", "https://apply.example.com", null, createdAt, "EXACT");
    }
}

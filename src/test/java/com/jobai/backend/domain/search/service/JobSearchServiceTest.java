package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    private ResumesRepository resumesRepository;
    private PrivateMatchScoreRepository privateMatchScoreRepository;
    private PublicMatchScoreRepository publicMatchScoreRepository;
    private QueryExpander queryExpander;
    private HybridSearchMerger hybridSearchMerger;
    private SearchReranker searchReranker;
    private JobSearchService jobSearchService;

    @BeforeEach
    void setUp() {
        keywordMatcher = Mockito.mock(KeywordMatcher.class);
        jobSearchRepository = Mockito.mock(JobSearchRepository.class);
        embeddingService = Mockito.mock(EmbeddingService.class);
        vectorSearchRepository = Mockito.mock(VectorSearchRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        privateMatchScoreRepository = Mockito.mock(PrivateMatchScoreRepository.class);
        publicMatchScoreRepository = Mockito.mock(PublicMatchScoreRepository.class);
        queryExpander = Mockito.mock(QueryExpander.class);
        hybridSearchMerger = Mockito.mock(HybridSearchMerger.class);
        searchReranker = Mockito.mock(SearchReranker.class);

        jobSearchService = new JobSearchService(
                keywordMatcher, jobSearchRepository,
                embeddingService, vectorSearchRepository,
                resumesRepository, privateMatchScoreRepository, publicMatchScoreRepository,
                queryExpander, hybridSearchMerger, searchReranker);

        ReflectionTestUtils.setField(jobSearchService, "embeddingEnabled", true);
        ReflectionTestUtils.setField(jobSearchService, "hybridEnabled", false);

        // 기본: 쿼리 확장 미적용, 리랭킹 미적용 (원본 그대로 반환)
        when(queryExpander.expand(anyString(), anyList()))
                .thenAnswer(inv -> QueryExpansionResult.unchanged(inv.getArgument(0)));
        when(searchReranker.rerank(anyString(), anyList()))
                .thenAnswer(inv -> inv.getArgument(1));
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

        JobSearchResponse response = jobSearchService.search("서울 백엔드", 0, 20, null);

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

        JobSearchResponse response = jobSearchService.search("서울에서 혼자 일하기 쉬운 백엔드", 0, 20, null);

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

        jobSearchService.search("판교에서 혼자 일하기 좋은 경력직 백엔드", 0, 20, null);

        Mockito.verify(vectorSearchRepository).searchPrivateByVector(
                any(), anyDouble(),
                argThat((SearchCondition cond) -> cond.categories().contains("백엔드")
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

        JobSearchResponse response = jobSearchService.search("혼자 일하기 편한 직무", 0, 20, null);

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

        JobSearchResponse response = jobSearchService.search("혼자 일하기 좋은 백엔드", 0, 20, null);

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
                        new ScoredJob(prv1, 0.20),
                        new ScoredJob(prv2, 0.35)
                ));

        JobSummary pub1 = createJobSummary(101L, "PUBLIC", "공기업A", LocalDateTime.of(2025, 6, 4, 10, 0));
        when(vectorSearchRepository.searchPublicByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        new ScoredJob(pub1, 0.10)
                ));

        when(vectorSearchRepository.countPrivateByVector(any(), anyDouble(), any())).thenReturn(2L);
        when(vectorSearchRepository.countPublicByVector(any(), anyDouble(), any())).thenReturn(1L);

        JobSearchResponse response = jobSearchService.search("혼자 일하기 편한", 0, 3, null);

        assertThat(response.jobs().stream().map(JobSummary::title).toList())
                .containsExactly("공기업A", "사기업A", "사기업B");
    }

    // --- embeddingEnabled = false ---

    @Test
    @DisplayName("벡터 비활성화 시 미매칭 있어도 기존 검색")
    void 벡터비활성화() {
        ReflectionTestUtils.setField(jobSearchService, "embeddingEnabled", false);

        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of(), null, null, null, List.of(), List.of(), List.of("혼자")));

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.countPrivate(any())).thenReturn(0L);
        when(jobSearchRepository.countPublic(any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("혼자 일하기 편한 직무", 0, 20, null);

        assertThat(response.searchInfo().method()).isEqualTo("KEYWORD");
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorSearchRepository);
    }

    // --- 하이브리드 검색 ---

    @Test
    @DisplayName("hybridEnabled 시 키워드+벡터 동시 실행 후 RRF 병합")
    void 하이브리드_검색() {
        ReflectionTestUtils.setField(jobSearchService, "hybridEnabled", true);

        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), List.of("혼자")));

        // 키워드와 벡터에 서로 다른 문서 반환
        JobSummary keywordJob = createJobSummary(1L, "PRIVATE", "키워드결과", LocalDateTime.now());
        JobSummary vectorJob = createJobSummary(2L, "PRIVATE", "벡터결과", LocalDateTime.now());

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of(keywordJob));
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.countPrivate(any())).thenReturn(1L);
        when(jobSearchRepository.countPublic(any())).thenReturn(0L);

        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});
        when(vectorSearchRepository.searchPrivateByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new ScoredJob(vectorJob, 0.15)));
        when(vectorSearchRepository.searchPublicByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        // merge에 전달된 인자를 캡처하여 실제 데이터 검증
        when(hybridSearchMerger.merge(anyList(), anyList()))
                .thenAnswer(inv -> {
                    List<JobSummary> kw = inv.getArgument(0);
                    List<JobSummary> vec = inv.getArgument(1);
                    // 키워드/벡터 결과가 실제로 전달되었는지 검증
                    assertThat(kw).extracting(JobSummary::title).contains("키워드결과");
                    assertThat(vec).extracting(JobSummary::title).contains("벡터결과");
                    return List.of(keywordJob, vectorJob);
                });

        JobSearchResponse response = jobSearchService.search("혼자 일하기 좋은 백엔드", 0, 20, null);

        assertThat(response.searchInfo().method()).isEqualTo("HYBRID");
        assertThat(response.jobs()).hasSize(2);
        Mockito.verify(hybridSearchMerger).merge(anyList(), anyList());
    }

    @Test
    @DisplayName("하이브리드 중 벡터 실패 시 키워드 결과만으로 RRF")
    void 하이브리드_벡터실패() {
        ReflectionTestUtils.setField(jobSearchService, "hybridEnabled", true);

        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), List.of("혼자")));

        JobSummary keywordJob = createJobSummary(1L, "PRIVATE", "키워드결과", LocalDateTime.now());
        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of(keywordJob));
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.countPrivate(any())).thenReturn(1L);
        when(jobSearchRepository.countPublic(any())).thenReturn(0L);

        when(embeddingService.embedQuery(anyString()))
                .thenThrow(new RuntimeException("ai-server 장애"));

        when(hybridSearchMerger.merge(anyList(), anyList()))
                .thenReturn(List.of(keywordJob));

        JobSearchResponse response = jobSearchService.search("혼자 일하기 좋은 백엔드", 0, 20, null);

        assertThat(response.searchInfo().method()).isEqualTo("HYBRID");
        // 벡터 결과는 빈 리스트로 merge에 전달됨
        Mockito.verify(hybridSearchMerger).merge(
                argThat((List<JobSummary> kw) -> !kw.isEmpty()),
                eq(List.of()));
    }

    // --- 쿼리 확장 키워드가 SearchInfo에 반영 ---

    @Test
    @DisplayName("쿼리 확장 시 expandedKeywords가 SearchInfo에 반영되고 확장된 텍스트로 임베딩")
    void 확장키워드_SearchInfo_반영() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), List.of("재택근무")));

        String expandedText = "재택근무 백엔드 원격근무 리모트워크";
        when(queryExpander.expand(anyString(), anyList()))
                .thenReturn(new QueryExpansionResult(expandedText,
                        List.of("원격근무", "리모트워크")));

        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});
        when(vectorSearchRepository.searchPrivateByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vectorSearchRepository.searchPublicByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(vectorSearchRepository.countPrivateByVector(any(), anyDouble(), any())).thenReturn(0L);
        when(vectorSearchRepository.countPublicByVector(any(), anyDouble(), any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("재택근무 가능한 백엔드", 0, 20, null);

        assertThat(response.searchInfo().expandedKeywords())
                .containsExactly("원격근무", "리모트워크");
        // 확장된 텍스트가 임베딩에 사용되었는지 검증
        Mockito.verify(embeddingService).embedQuery(eq(expandedText));
    }

    // --- 헬퍼 ---

    private static JobSummary createJobSummary(Long id, String source, String title,
                                                LocalDateTime createdAt) {
        return JobSummary.of(id, source, title, "회사", "서울", "백엔드",
                "정규직", "신입", "https://apply.example.com", null, createdAt, "EXACT");
    }
}

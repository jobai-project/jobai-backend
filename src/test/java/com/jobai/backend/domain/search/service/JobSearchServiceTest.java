package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.dto.JobSearchResponse.JobSummary;
import com.jobai.backend.domain.search.dto.QueryExpansionResult;
import com.jobai.backend.domain.search.dto.RequirementGroup;
import com.jobai.backend.domain.search.dto.SearchCondition;
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

        when(queryExpander.expand(anyString(), anyList()))
                .thenAnswer(inv -> QueryExpansionResult.unchanged(inv.getArgument(0)));

        // rerank 기본 동작: 입력 리스트를 그대로 반환 (disabled 상태와 동일)
        when(searchReranker.rerank(anyString(), anyList()))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    // ─── Path A: unmatchedTokens 없음 → 키워드 검색 ───────────────────────────

    @Test
    @DisplayName("미매칭 토큰 없으면 키워드 검색, 벡터 미사용")
    void 미매칭없으면_키워드검색() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, "서울", null, List.of(), List.of(), null, List.of()));

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.countPrivate(any())).thenReturn(0L);
        when(jobSearchRepository.countPublic(any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("서울 백엔드", 0, 20, null);

        assertThat(response.searchInfo().method()).isEqualTo("KEYWORD");
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorSearchRepository);
    }

    // ─── Path C: unmatchedTokens 있음, hybridEnabled=false → 순수 벡터 검색 ──

    @Test
    @DisplayName("미매칭 토큰 있으면 벡터 검색 실행 (Path C)")
    void 미매칭있으면_벡터검색() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, "서울", null, List.of(), List.of(), null, List.of("혼자", "일하기")));

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
                .thenReturn(new MatchResult(List.of("백엔드"), null, "판교", "경력", List.of("경력", "무관"), List.of(), null, List.of("혼자")));

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

    @Test
    @DisplayName("벡터 검색 결과 0건이면 빈 결과 반환 (폴백 없음)")
    void 벡터검색_결과없으면_빈결과() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of(), null, null, null, List.of(), List.of(), null, List.of("혼자", "일하기")));

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

    @Test
    @DisplayName("임베딩 실패 시 키워드 검색으로 폴백, method=KEYWORD")
    void 임베딩실패시_폴백() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), null, List.of("혼자")));

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

    @Test
    @DisplayName("사기업 벡터 유사도순(distance 오름차순) 정렬 (기본 검색에서 공공기관 제외)")
    void 사기업_유사도순정렬() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of(), null, null, null, List.of(), List.of(), null, List.of("혼자")));

        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});

        JobSummary prv1 = createJobSummary(1L, "PRIVATE", "사기업A", LocalDateTime.of(2025, 6, 5, 10, 0));
        JobSummary prv2 = createJobSummary(2L, "PRIVATE", "사기업B", LocalDateTime.of(2025, 6, 3, 10, 0));
        when(vectorSearchRepository.searchPrivateByVector(any(), anyDouble(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new ScoredJob(prv1, 0.20), new ScoredJob(prv2, 0.35)));

        when(vectorSearchRepository.countPrivateByVector(any(), anyDouble(), any())).thenReturn(2L);

        JobSearchResponse response = jobSearchService.search("혼자 일하기 편한", 0, 3, null);

        assertThat(response.jobs().stream().map(JobSummary::title).toList())
                .containsExactly("사기업A", "사기업B");
        verifyNoInteractions(jobSearchRepository);
    }

    @Test
    @DisplayName("embeddingEnabled=false 이면 미매칭 있어도 키워드 검색")
    void 벡터비활성화() {
        ReflectionTestUtils.setField(jobSearchService, "embeddingEnabled", false);

        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of(), null, null, null, List.of(), List.of(), null, List.of("혼자")));

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.countPrivate(any())).thenReturn(0L);
        when(jobSearchRepository.countPublic(any())).thenReturn(0L);

        JobSearchResponse response = jobSearchService.search("혼자 일하기 편한 직무", 0, 20, null);

        assertThat(response.searchInfo().method()).isEqualTo("KEYWORD");
        verifyNoInteractions(embeddingService);
        verifyNoInteractions(vectorSearchRepository);
    }

    // ─── Path B: hybridEnabled=true → 필터 후 그룹 인식 벡터 재정렬 ──────────

    @Test
    @DisplayName("hybridEnabled 시 필터+벡터 재정렬 실행, 벡터 유사도 순 반환 (Path B)")
    void 하이브리드_필터후벡터재정렬() {
        ReflectionTestUtils.setField(jobSearchService, "hybridEnabled", true);

        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), null, List.of("혼자")));

        JobSummary job1 = createJobSummary(1L, "PRIVATE", "백엔드A", LocalDateTime.now());
        JobSummary job2 = createJobSummary(2L, "PRIVATE", "백엔드B", LocalDateTime.now());

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of(job1, job2));
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());

        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});
        // job2가 더 가까운 벡터 거리
        when(vectorSearchRepository.rankPrivateByVector(any(), anyList()))
                .thenReturn(List.of(new ScoredJob(job2, 0.10), new ScoredJob(job1, 0.20)));
        when(vectorSearchRepository.rankPublicByVector(any(), anyList())).thenReturn(List.of());

        JobSearchResponse response = jobSearchService.search("혼자 일하기 좋은 백엔드", 0, 20, null);

        assertThat(response.searchInfo().method()).isEqualTo("HYBRID");
        assertThat(response.jobs()).hasSize(2);
        assertThat(response.jobs().get(0).title()).isEqualTo("백엔드B");
        assertThat(response.jobs().get(1).title()).isEqualTo("백엔드A");
        assertThat(response.jobs()).allMatch(j -> "STRICT".equals(j.matchLevel()));
        verifyNoInteractions(hybridSearchMerger);
    }

    @Test
    @DisplayName("임베딩 실패 시 matchLevel 순 폴백 정렬 (Path B)")
    void 하이브리드_임베딩실패시_폴백정렬() {
        ReflectionTestUtils.setField(jobSearchService, "hybridEnabled", true);

        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), null, List.of("혼자")));

        JobSummary job1 = createJobSummary(1L, "PRIVATE", "백엔드A", LocalDateTime.now());
        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of(job1));
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());

        when(embeddingService.embedQuery(anyString())).thenThrow(new RuntimeException("임베딩 실패"));

        JobSearchResponse response = jobSearchService.search("혼자 일하기 좋은 백엔드", 0, 20, null);

        assertThat(response.searchInfo().method()).isEqualTo("HYBRID");
        assertThat(response.jobs()).hasSize(1);
        assertThat(response.jobs().get(0).matchLevel()).isEqualTo("STRICT");
        verifyNoInteractions(hybridSearchMerger);
    }

    // ─── 쿼리 확장 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("쿼리 확장 시 expandedKeywords가 SearchInfo에 반영되고 확장 텍스트로 임베딩")
    void 확장키워드_SearchInfo_반영() {
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), null, List.of("재택근무")));

        String expandedText = "재택근무 백엔드 원격근무 리모트워크";
        when(queryExpander.expand(anyString(), anyList()))
                .thenReturn(new QueryExpansionResult(
                        expandedText,
                        List.of("원격근무", "리모트워크"),
                        List.of(),  // exactRequired
                        List.of(),  // semanticRequired
                        List.of()   // semanticPreferred
                ));

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
        Mockito.verify(embeddingService).embedQuery(eq(expandedText));
    }

    @Test
    @DisplayName("exactRequired 있으면 Path B에서 STRICT 후보 필터에 exactGroups 전달")
    void exactRequired_있으면_필터에전달() {
        ReflectionTestUtils.setField(jobSearchService, "hybridEnabled", true);

        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of("백엔드"), null, null, null, List.of(), List.of(), null, List.of("kafka")));

        RequirementGroup kafkaGroup = new RequirementGroup("KAFKA", List.of("kafka", "apache kafka"));
        when(queryExpander.expand(anyString(), anyList()))
                .thenReturn(new QueryExpansionResult(
                        "백엔드 kafka",
                        List.of(),
                        List.of(kafkaGroup),  // exactRequired
                        List.of(),            // semanticRequired
                        List.of()             // semanticPreferred
                ));

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});

        jobSearchService.search("kafka 쓰는 백엔드", 0, 20, null);

        // STRICT 조건으로 searchPrivate 호출 시 exactGroups가 포함되어야 함
        Mockito.verify(jobSearchRepository, Mockito.atLeastOnce()).searchPrivate(
                argThat(cond -> cond.exactGroups() != null && !cond.exactGroups().isEmpty()),
                anyInt(), anyInt());
    }

    // ─── title LIKE fallback ──────────────────────────────────────────────────

    @Test
    @DisplayName("QueryExpander 미분류 시 unmatchedTokens가 exactRequired로 변환되어 title LIKE 필터 적용")
    void QueryExpander미분류시_unmatchedTokens_exactRequired_변환() {
        ReflectionTestUtils.setField(jobSearchService, "hybridEnabled", true);

        // "커머스"는 구조화 조건 미인식 → unmatchedTokens
        when(keywordMatcher.extract(anyString()))
                .thenReturn(new MatchResult(List.of(), null, null, null, List.of(), List.of(), null, List.of("커머스")));

        // QueryExpander가 아무것도 분류하지 않은 상태 (비활성 또는 실패)
        when(queryExpander.expand(anyString(), anyList()))
                .thenReturn(QueryExpansionResult.unchanged("커머스"));

        when(jobSearchRepository.searchPrivate(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(jobSearchRepository.searchPublic(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{0.1f});

        jobSearchService.search("커머스", 0, 20, null);

        // exactGroups에 "커머스"가 포함된 조건으로 searchPrivate가 호출되어야 함
        Mockito.verify(jobSearchRepository, Mockito.atLeastOnce()).searchPrivate(
                argThat(cond -> cond.exactGroups() != null
                        && !cond.exactGroups().isEmpty()
                        && cond.exactGroups().get(0).terms().contains("커머스")),
                anyInt(), anyInt());
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────────────

    private static JobSummary createJobSummary(Long id, String source, String title,
                                                LocalDateTime createdAt) {
        return JobSummary.of(id, source, title, "회사", "서울", "백엔드",
                "정규직", "신입", "https://apply.example.com", null, createdAt, "EXACT");
    }
}

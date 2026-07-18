package com.jobai.backend.domain.home.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.ai.client.AiScoringClient;
import com.jobai.backend.global.ai.dto.ScorePublicRequest;
import com.jobai.backend.global.ai.dto.ScorePublicResponse;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.global.model.JobSource;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import com.jobai.backend.domain.search.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicMatchingServiceTest {

    private AiScoringClient aiScoringClient;
    private JobPostingRepository jobPostingRepository;
    private JobEmbeddingRepository jobEmbeddingRepository;
    private EmbeddingService embeddingService;
    private PublicMatchScoreRepository publicMatchScoreRepository;
    private ResumesRepository resumesRepository;
    private ObjectMapper objectMapper;

    private PublicMatchingService service;

    private final AtomicLong postingIdCounter = new AtomicLong(200);

    @BeforeEach
    void setUp() {
        aiScoringClient = Mockito.mock(AiScoringClient.class);
        jobPostingRepository = Mockito.mock(JobPostingRepository.class);
        jobEmbeddingRepository = Mockito.mock(JobEmbeddingRepository.class);
        embeddingService = Mockito.mock(EmbeddingService.class);
        publicMatchScoreRepository = Mockito.mock(PublicMatchScoreRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        objectMapper = new ObjectMapper();

        service = new PublicMatchingService(
                aiScoringClient,
                jobPostingRepository,
                jobEmbeddingRepository,
                embeddingService,
                publicMatchScoreRepository,
                resumesRepository,
                objectMapper
        );
    }

    private Member createMember(String careerType) {
        return Member.builder()
                .email("test@example.com")
                .careerTypes(careerType == null ? List.of() : List.of(careerType))
                .build();
    }

    private Resumes createResume(Member member, float[] ncsEmbedding, String skills) {
        return Resumes.builder()
                .member(member)
                .extractedText("이력서 텍스트")
                .ncsEmbedding(ncsEmbedding)
                .resumeSkills(skills)
                .isActive(true)
                .build();
    }

    private PublicJobPosting createPosting(String title, boolean closed) {
        return PublicJobPosting.builder()
                .id(postingIdCounter.getAndIncrement())
                .title(title)
                .companyName("한국테스트공사")
                .jobRole("전산")
                .workExperience("신입")
                .recrutType("정규직")
                .applyQualification("자격요건")
                .applicationMethod("온라인 접수")
                .htmlContent("<p>본문</p>")
                .isClosed(closed)
                .build();
    }

    private float[] dummyEmbedding() {
        return new float[]{0.1f, 0.2f, 0.3f};
    }

    private ScorePublicResponse dummyScoreResponse(double score) {
        return new ScorePublicResponse(
                score, true, 0.5, 0.6, 0.7, -5.0,
                List.of("Python"), List.of("Linux"),
                List.of(), List.of(),
                "데이터/AI", "데이터/AI",
                "직무 클러스터 일치", List.of()
        );
    }

    @Test
    @DisplayName("이력서가 없으면 점수 계산을 건너뛴다")
    void calculateScores_이력서없음() {
        when(resumesRepository.findById(1L)).thenReturn(Optional.empty());

        service.calculateScores(1L);

        verifyNoInteractions(publicMatchScoreRepository);
        verifyNoInteractions(aiScoringClient);
    }

    @Test
    @DisplayName("이력서 NCS 임베딩이 없으면 점수 계산을 건너뛴다")
    void calculateScores_임베딩없음() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, null, null);
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        service.calculateScores(1L);

        verifyNoInteractions(aiScoringClient);
        verify(publicMatchScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("활성 공고가 없으면 점수 계산을 건너뛴다")
    void calculateScores_활성공고없음() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of());

        service.calculateScores(1L);

        verify(publicMatchScoreRepository).deleteByResumeId(1L);
        verify(aiScoringClient, never()).scorePublic(any());
    }

    @Test
    @DisplayName("정상적으로 매칭 점수를 계산하고 저장한다")
    void calculateScores_정상계산() {
        Member member = createMember("경력직");
        Resumes resume = createResume(member, dummyEmbedding(), "[\"Python\",\"SQL\"]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting = createPosting("데이터 분석원", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        JobEmbedding jobEmbedding = JobEmbedding.builder()
                .source(JobSource.PUBLIC)
                .sourceId(posting.getId())
                .embedding(new float[]{0.4f, 0.5f, 0.6f})
                .embeddingText("데이터 분석원\n본문")
                .build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.of(jobEmbedding));

        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(85.5)));

        service.calculateScores(1L);

        verify(publicMatchScoreRepository).deleteByResumeId(1L);
        verify(publicMatchScoreRepository).save(argThat(score -> {
            assertThat(score.getScore()).isEqualTo(86); // Math.round(85.5)
            assertThat(score.getScoreReason()).isEqualTo("직무 클러스터 일치");
            assertThat(score.getJobCluster()).isEqualTo("데이터/AI");
            assertThat(score.getResumeCluster()).isEqualTo("데이터/AI");
            return true;
        }));
    }

    @Test
    @DisplayName("공고 임베딩이 없으면 새로 생성한다")
    void calculateScores_임베딩생성() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), "[\"Python\"]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting = createPosting("전산 주무관", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        JobEmbedding created = JobEmbedding.builder()
                .source(JobSource.PUBLIC)
                .sourceId(posting.getId())
                .embedding(new float[]{0.7f, 0.8f})
                .embeddingText("전산 주무관\n본문")
                .build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));

        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(72.0)));

        service.calculateScores(1L);

        verify(embeddingService).embedPublicPosting(posting);
        verify(publicMatchScoreRepository).save(any());
    }

    @Test
    @DisplayName("공고 임베딩 생성 실패 시 해당 공고를 건너뛰고 계속한다")
    void calculateScores_임베딩생성실패_계속진행() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), "[]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting1 = createPosting("공고1", false);
        PublicJobPosting posting2 = createPosting("공고2", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting1, posting2));

        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting1.getId()))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("AI 서버 오류"))
                .when(embeddingService).embedPublicPosting(posting1);

        JobEmbedding embed2 = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting2.getId())
                .embedding(new float[]{0.1f}).embeddingText("text").build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting2.getId()))
                .thenReturn(Optional.of(embed2));
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(90.0)));

        service.calculateScores(1L);

        verify(publicMatchScoreRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("AI 스코어링 호출 실패 시 해당 공고를 건너뛰고 계속한다")
    void calculateScores_스코어링실패_계속진행() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), "[]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting = createPosting("공고", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        JobEmbedding embed = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting.getId())
                .embedding(new float[]{0.1f}).embeddingText("text").build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.of(embed));
        when(aiScoringClient.scorePublic(any()))
                .thenThrow(new RuntimeException("AI 서버 타임아웃"));

        service.calculateScores(1L);

        verify(publicMatchScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("경력직 회원은 experienceYears가 3으로 설정된다")
    void calculateScores_경력회원_연수3() {
        Member member = createMember("경력직");
        Resumes resume = createResume(member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting = createPosting("개발자", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        JobEmbedding embed = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting.getId())
                .embedding(new float[]{0.1f}).embeddingText("text").build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.of(embed));
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(80.0)));

        service.calculateScores(1L);

        ArgumentCaptor<ScorePublicRequest> captor = ArgumentCaptor.forClass(ScorePublicRequest.class);
        verify(aiScoringClient).scorePublic(captor.capture());
        assertThat(captor.getValue().resume().experienceYears()).isEqualTo(3);
    }

    @Test
    @DisplayName("신입 회원은 experienceYears가 0으로 설정된다")
    void calculateScores_신입회원_연수0() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), "[\"Python\"]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting = createPosting("개발자", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        JobEmbedding embed = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting.getId())
                .embedding(new float[]{0.1f}).embeddingText("text").build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.of(embed));
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(70.0)));

        service.calculateScores(1L);

        ArgumentCaptor<ScorePublicRequest> captor = ArgumentCaptor.forClass(ScorePublicRequest.class);
        verify(aiScoringClient).scorePublic(captor.capture());
        assertThat(captor.getValue().resume().experienceYears()).isEqualTo(0);
    }

    @Test
    @DisplayName("resumeSkills가 null이면 빈 리스트로 전달된다")
    void calculateScores_스킬null_빈리스트() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), null);
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting = createPosting("개발자", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        JobEmbedding embed = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting.getId())
                .embedding(new float[]{0.1f}).embeddingText("text").build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.of(embed));
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(60.0)));

        service.calculateScores(1L);

        ArgumentCaptor<ScorePublicRequest> captor = ArgumentCaptor.forClass(ScorePublicRequest.class);
        verify(aiScoringClient).scorePublic(captor.capture());
        assertThat(captor.getValue().resume().skills()).isEmpty();
    }

    @Test
    @DisplayName("자격증(certs)과 희망직무(job_role)는 항상 빈 값으로 전달된다 (미구현 필드)")
    void calculateScores_certs_jobRole_항상빈값() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting = createPosting("개발자", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        JobEmbedding embed = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting.getId())
                .embedding(new float[]{0.1f}).embeddingText("text").build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.of(embed));
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(65.0)));

        service.calculateScores(1L);

        ArgumentCaptor<ScorePublicRequest> captor = ArgumentCaptor.forClass(ScorePublicRequest.class);
        verify(aiScoringClient).scorePublic(captor.capture());
        assertThat(captor.getValue().resume().certs()).isEmpty();
        assertThat(captor.getValue().resume().jobRole()).isEmpty();
    }

    @Test
    @DisplayName("여러 공고에 대해 각각 점수를 계산하고 저장한다")
    void calculateScores_여러공고_각각저장() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting1 = createPosting("공고A", false);
        PublicJobPosting posting2 = createPosting("공고B", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting1, posting2));

        JobEmbedding embed1 = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting1.getId())
                .embedding(new float[]{0.1f}).embeddingText("text1").build();
        JobEmbedding embed2 = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting2.getId())
                .embedding(new float[]{0.2f}).embeddingText("text2").build();

        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting1.getId()))
                .thenReturn(Optional.of(embed1));
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting2.getId()))
                .thenReturn(Optional.of(embed2));

        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(85.0)))
                .thenReturn(Mono.just(dummyScoreResponse(72.0)));

        service.calculateScores(1L);

        verify(publicMatchScoreRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("기존 점수를 삭제한 후 재계산한다")
    void calculateScores_기존점수삭제후_재계산() {
        Member member = createMember("신입");
        Resumes resume = createResume(member, dummyEmbedding(), "[]");
        when(resumesRepository.findById(1L)).thenReturn(Optional.of(resume));

        PublicJobPosting posting = createPosting("공고", false);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        JobEmbedding embed = JobEmbedding.builder()
                .source(JobSource.PUBLIC).sourceId(posting.getId())
                .embedding(new float[]{0.1f}).embeddingText("text").build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.of(embed));
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(80.0)));

        service.calculateScores(1L);

        var inOrder = inOrder(publicMatchScoreRepository);
        inOrder.verify(publicMatchScoreRepository).deleteByResumeId(1L);
        inOrder.verify(publicMatchScoreRepository).save(any());
    }
}

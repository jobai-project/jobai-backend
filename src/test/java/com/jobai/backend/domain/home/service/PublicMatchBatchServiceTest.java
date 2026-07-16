package com.jobai.backend.domain.home.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.ai.client.AiScoringClient;
import com.jobai.backend.domain.ai.dto.ScorePublicResponse;
import com.jobai.backend.domain.home.entity.PublicMatchScore;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.domain.search.entity.JobSource;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import com.jobai.backend.domain.search.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublicMatchBatchServiceTest {

    private AiScoringClient aiScoringClient;
    private JobPostingRepository jobPostingRepository;
    private JobEmbeddingRepository jobEmbeddingRepository;
    private EmbeddingService embeddingService;
    private PublicMatchScoreRepository publicMatchScoreRepository;
    private ResumesRepository resumesRepository;
    private ObjectMapper objectMapper;
    private NotificationRepository notificationRepository;
    private BatchNotificationHelper batchNotificationHelper;

    private PublicMatchBatchService service;

    private final AtomicLong postingIdCounter = new AtomicLong(200);

    @BeforeEach
    void setUp() throws Exception {
        aiScoringClient = Mockito.mock(AiScoringClient.class);
        jobPostingRepository = Mockito.mock(JobPostingRepository.class);
        jobEmbeddingRepository = Mockito.mock(JobEmbeddingRepository.class);
        embeddingService = Mockito.mock(EmbeddingService.class);
        publicMatchScoreRepository = Mockito.mock(PublicMatchScoreRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        objectMapper = new ObjectMapper();
        notificationRepository = Mockito.mock(NotificationRepository.class);
        batchNotificationHelper = Mockito.mock(BatchNotificationHelper.class);

        service = new PublicMatchBatchService(
                aiScoringClient,
                jobPostingRepository,
                jobEmbeddingRepository,
                embeddingService,
                publicMatchScoreRepository,
                resumesRepository,
                objectMapper,
                notificationRepository,
                batchNotificationHelper
        );

        // self-injection 필드를 리플렉션으로 설정 (단위 테스트에서는 프록시 없이 자기 자신 주입)
        var selfField = PublicMatchBatchService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(service, service);
    }

    // ── 헬퍼 메서드 ──

    private Member createMember(String careerType) {
        return Member.builder()
                .email("test@example.com")
                .careerTypes(careerType == null ? List.of() : List.of(careerType))
                .build();
    }

    private Resumes createResume(Long id, Member member, float[] ncsEmbedding, String skills) {
        return Resumes.builder()
                .id(id)
                .member(member)
                .extractedText("이력서 텍스트")
                .ncsEmbedding(ncsEmbedding)
                .resumeSkills(skills)
                .isActive(true)
                .build();
    }

    private PublicJobPosting createPosting(String title, LocalDateTime updatedAt) {
        return PublicJobPosting.builder()
                .id(postingIdCounter.getAndIncrement())
                .title(title)
                .companyName("한국테스트공사")
                .jobRole("전산")
                .workExperience("신입")
                .recrutType("정규직")
                .htmlContent("<p>본문</p>")
                .isClosed(false)
                .updatedAt(updatedAt)
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

    private PublicMatchScore createExistingScore(Resumes resume, PublicJobPosting posting,
                                                  LocalDateTime createdAt) {
        return PublicMatchScore.builder()
                .id(1L)
                .member(resume.getMember())
                .resume(resume)
                .publicJobPosting(posting)
                .score(80)
                .scoreReason("이전 점수")
                .matchedSkills("[]")
                .missingSkills("[]")
                .matchedCerts("[]")
                .missingCerts("[]")
                .jobCluster("데이터/AI")
                .resumeCluster("데이터/AI")
                .createdAt(createdAt)
                .build();
    }

    private void stubEmbedding(PublicJobPosting posting) {
        JobEmbedding embed = JobEmbedding.builder()
                .source(JobSource.PUBLIC)
                .sourceId(posting.getId())
                .embedding(new float[]{0.4f, 0.5f})
                .embeddingText("text")
                .build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting.getId()))
                .thenReturn(Optional.of(embed));
    }

    // ── 테스트 ──

    @Test
    @DisplayName("활성 이력서가 없으면 점수 산출을 건너뛴다")
    void scoreNewAndUpdatedPostings_이력서없음() {
        when(resumesRepository.findAllActiveWithNcsEmbedding()).thenReturn(List.of());

        service.scoreNewAndUpdatedPostings();

        verifyNoInteractions(jobPostingRepository);
        verifyNoInteractions(aiScoringClient);
    }

    @Test
    @DisplayName("활성 공고가 없으면 점수 산출을 건너뛴다")
    void scoreNewAndUpdatedPostings_공고없음() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findAllActiveWithNcsEmbedding()).thenReturn(List.of(resume));
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of());

        service.scoreNewAndUpdatedPostings();

        verifyNoInteractions(aiScoringClient);
        verify(publicMatchScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 공고(점수 없음)에 대해 AI 점수를 산출하고 저장한다")
    void scoreNewAndUpdatedPostings_신규공고_점수산출() {
        Member member = createMember("경력직");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Python\",\"SQL\"]");
        when(resumesRepository.findAllActiveWithNcsEmbedding()).thenReturn(List.of(resume));

        PublicJobPosting posting = createPosting("데이터 분석원", LocalDateTime.now());
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        when(publicMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of());

        stubEmbedding(posting);
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(85.5)));

        service.scoreNewAndUpdatedPostings();

        verify(publicMatchScoreRepository).save(argThat(score -> {
            assertThat(score.getScore()).isEqualTo(86);
            assertThat(score.getScoreReason()).isEqualTo("직무 클러스터 일치");
            assertThat(score.getPublicJobPosting()).isEqualTo(posting);
            return true;
        }));
    }

    @Test
    @DisplayName("변경된 공고(posting.updatedAt > score.createdAt)는 기존 점수 삭제 후 재산출한다")
    void scoreNewAndUpdatedPostings_변경공고_재산출() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findAllActiveWithNcsEmbedding()).thenReturn(List.of(resume));

        LocalDateTime scoreCreatedAt = LocalDateTime.of(2026, 7, 1, 2, 0);
        LocalDateTime postingUpdatedAt = LocalDateTime.of(2026, 7, 5, 10, 0);
        PublicJobPosting posting = createPosting("전산 주무관", postingUpdatedAt);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        PublicMatchScore existingScore = createExistingScore(resume, posting, scoreCreatedAt);
        when(publicMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of(existingScore));

        stubEmbedding(posting);
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(90.0)));

        service.scoreNewAndUpdatedPostings();

        var inOrder = inOrder(publicMatchScoreRepository);
        inOrder.verify(publicMatchScoreRepository).delete(existingScore);
        inOrder.verify(publicMatchScoreRepository).flush();
        inOrder.verify(publicMatchScoreRepository).save(argThat(score -> {
            assertThat(score.getScore()).isEqualTo(90);
            return true;
        }));
    }

    @Test
    @DisplayName("변경 재산출 중 AI 호출 실패 시 예외가 전파된다 (트랜잭션 롤백 유도)")
    void scoreForResume_변경재산출실패_예외전파() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");

        LocalDateTime scoreCreatedAt = LocalDateTime.of(2026, 7, 1, 2, 0);
        LocalDateTime postingUpdatedAt = LocalDateTime.of(2026, 7, 5, 10, 0);
        PublicJobPosting posting = createPosting("전산 주무관", postingUpdatedAt);

        PublicMatchScore existingScore = createExistingScore(resume, posting, scoreCreatedAt);
        when(publicMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of(existingScore));

        stubEmbedding(posting);
        when(aiScoringClient.scorePublic(any()))
                .thenThrow(new RuntimeException("AI 서버 타임아웃"));

        Map<Long, PublicJobPosting> postingMap = Map.of(posting.getId(), posting);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.scoreForResume(resume, postingMap));
    }

    @Test
    @DisplayName("기존 점수가 있고 변경 없는 공고는 건너뛴다")
    void scoreNewAndUpdatedPostings_변경없음_스킵() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findAllActiveWithNcsEmbedding()).thenReturn(List.of(resume));

        LocalDateTime scoreCreatedAt = LocalDateTime.of(2026, 7, 5, 2, 0);
        LocalDateTime postingUpdatedAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        PublicJobPosting posting = createPosting("전산 주무관", postingUpdatedAt);
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        PublicMatchScore existingScore = createExistingScore(resume, posting, scoreCreatedAt);
        when(publicMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of(existingScore));

        service.scoreNewAndUpdatedPostings();

        verifyNoInteractions(aiScoringClient);
        verify(publicMatchScoreRepository, never()).save(any());
        verify(publicMatchScoreRepository, never()).delete(any());
    }

    @Test
    @DisplayName("한 공고 점수 산출 실패 시에도 나머지 공고는 계속 처리된다")
    void scoreNewAndUpdatedPostings_부분실패_공고() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findAllActiveWithNcsEmbedding()).thenReturn(List.of(resume));

        PublicJobPosting posting1 = createPosting("공고1", LocalDateTime.now());
        PublicJobPosting posting2 = createPosting("공고2", LocalDateTime.now());
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting1, posting2));
        when(publicMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of());

        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PUBLIC, posting1.getId()))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("AI 서버 오류"))
                .when(embeddingService).embedPublicPosting(posting1);

        stubEmbedding(posting2);
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(75.0)));

        service.scoreNewAndUpdatedPostings();

        verify(publicMatchScoreRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("한 이력서 처리 실패 시에도 나머지 이력서는 계속 처리된다")
    void scoreNewAndUpdatedPostings_부분실패_이력서() {
        Member member1 = createMember("신입");
        Member member2 = createMember("경력직");
        Resumes resume1 = createResume(1L, member1, dummyEmbedding(), "[\"Java\"]");
        Resumes resume2 = createResume(2L, member2, dummyEmbedding(), "[\"Python\"]");
        when(resumesRepository.findAllActiveWithNcsEmbedding()).thenReturn(List.of(resume1, resume2));

        PublicJobPosting posting = createPosting("개발자", LocalDateTime.now());
        when(jobPostingRepository.findActivePublicPostings()).thenReturn(List.of(posting));

        when(publicMatchScoreRepository.findByResumeId(1L))
                .thenThrow(new RuntimeException("DB 오류"));

        when(publicMatchScoreRepository.findByResumeId(2L)).thenReturn(List.of());
        stubEmbedding(posting);
        when(aiScoringClient.scorePublic(any()))
                .thenReturn(Mono.just(dummyScoreResponse(80.0)));

        service.scoreNewAndUpdatedPostings();

        verify(publicMatchScoreRepository, times(1)).save(any());
    }
}

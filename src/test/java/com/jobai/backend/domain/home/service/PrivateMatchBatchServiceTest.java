package com.jobai.backend.domain.home.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.ai.client.AiScoringClient;
import com.jobai.backend.domain.ai.dto.ScorePrivateResponse;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
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

class PrivateMatchBatchServiceTest {

    private AiScoringClient aiScoringClient;
    private PrivateJobPostingRepository privateJobPostingRepository;
    private JobEmbeddingRepository jobEmbeddingRepository;
    private EmbeddingService embeddingService;
    private PrivateMatchScoreRepository privateMatchScoreRepository;
    private ResumesRepository resumesRepository;
    private ObjectMapper objectMapper;
    private NotificationRepository notificationRepository;
    private BatchNotificationHelper batchNotificationHelper;

    private PrivateMatchBatchService service;

    private final AtomicLong postingIdCounter = new AtomicLong(100);

    private static final List<String> VALID_CATEGORIES = List.of(
            "백엔드", "프론트엔드", "풀스택", "모바일", "AI/ML",
            "데이터엔지니어링", "DevOps/인프라", "보안", "QA/테스트",
            "임베디드", "기타개발", "UX리서처", "UX/UI디자이너",
            "프로덕트디자이너", "웹디자이너", "PM/PO", "서비스기획"
    );

    @BeforeEach
    void setUp() throws Exception {
        aiScoringClient = Mockito.mock(AiScoringClient.class);
        privateJobPostingRepository = Mockito.mock(PrivateJobPostingRepository.class);
        jobEmbeddingRepository = Mockito.mock(JobEmbeddingRepository.class);
        embeddingService = Mockito.mock(EmbeddingService.class);
        privateMatchScoreRepository = Mockito.mock(PrivateMatchScoreRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        objectMapper = new ObjectMapper();
        notificationRepository = Mockito.mock(NotificationRepository.class);
        batchNotificationHelper = Mockito.mock(BatchNotificationHelper.class);

        service = new PrivateMatchBatchService(
                aiScoringClient,
                privateJobPostingRepository,
                jobEmbeddingRepository,
                embeddingService,
                privateMatchScoreRepository,
                resumesRepository,
                objectMapper,
                notificationRepository,
                batchNotificationHelper
        );

        // self-injection 필드를 리플렉션으로 설정 (단위 테스트에서는 프록시 없이 자기 자신 주입)
        var selfField = PrivateMatchBatchService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(service, service);
    }

    // ── 헬퍼 메서드 ──

    private Member createMember(String careerType) {
        return Member.builder()
                .email("test@example.com")
                .careerType(careerType)
                .build();
    }

    private Resumes createResume(Long id, Member member, float[] embedding, String skills) {
        return Resumes.builder()
                .id(id)
                .member(member)
                .extractedText("이력서 텍스트")
                .embedding(embedding)
                .resumeSkills(skills)
                .isActive(true)
                .build();
    }

    private PrivateJobPosting createPosting(String title, String category, LocalDateTime updatedAt) {
        return PrivateJobPosting.builder()
                .id(postingIdCounter.getAndIncrement())
                .company("testcompany")
                .sourceJobId("job-" + title)
                .title(title)
                .description("설명")
                .jobCategory(category)
                .isClosed(false)
                .updatedAt(updatedAt)
                .build();
    }

    private float[] dummyEmbedding() {
        return new float[]{0.1f, 0.2f, 0.3f};
    }

    private ScorePrivateResponse dummyScoreResponse(double score) {
        return new ScorePrivateResponse(
                score, true, List.of("Java", "Spring"), List.of("Kubernetes"),
                true, "기술스택 일치", "v1.0"
        );
    }

    private PrivateMatchScore createExistingScore(Resumes resume, PrivateJobPosting posting,
                                                   LocalDateTime createdAt) {
        return PrivateMatchScore.builder()
                .id(1L)
                .member(resume.getMember())
                .resume(resume)
                .privateJobPosting(posting)
                .score(80)
                .scoreReason("이전 점수")
                .matchedSkills("[]")
                .missingSkills("[]")
                .careerMet(true)
                .modelVersion("v1.0")
                .createdAt(createdAt)
                .build();
    }

    private void stubEmbedding(PrivateJobPosting posting) {
        JobEmbedding embed = JobEmbedding.builder()
                .source(JobSource.PRIVATE)
                .sourceId(posting.getId())
                .embedding(new float[]{0.4f, 0.5f})
                .embeddingText("text")
                .build();
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PRIVATE, posting.getId()))
                .thenReturn(Optional.of(embed));
    }

    // ── 테스트 ──

    @Test
    @DisplayName("활성 이력서가 없으면 점수 산출을 건너뛴다")
    void scoreNewAndUpdatedPostings_이력서없음() {
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of());

        service.scoreNewAndUpdatedPostings();

        verifyNoInteractions(privateJobPostingRepository);
        verifyNoInteractions(aiScoringClient);
    }

    @Test
    @DisplayName("활성 공고가 없으면 점수 산출을 건너뛴다")
    void scoreNewAndUpdatedPostings_공고없음() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));
        when(privateJobPostingRepository.findActiveByValidCategories(VALID_CATEGORIES))
                .thenReturn(List.of());

        service.scoreNewAndUpdatedPostings();

        verifyNoInteractions(aiScoringClient);
        verify(privateMatchScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 공고(점수 없음)에 대해 AI 점수를 산출하고 저장한다")
    void scoreNewAndUpdatedPostings_신규공고_점수산출() {
        Member member = createMember("경력");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\",\"Spring\"]");
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));

        PrivateJobPosting posting = createPosting("백엔드 개발자", "백엔드", LocalDateTime.now());
        when(privateJobPostingRepository.findActiveByValidCategories(VALID_CATEGORIES))
                .thenReturn(List.of(posting));

        // 기존 점수 없음
        when(privateMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of());

        stubEmbedding(posting);
        when(aiScoringClient.scorePrivate(any()))
                .thenReturn(Mono.just(dummyScoreResponse(85.5)));

        service.scoreNewAndUpdatedPostings();

        verify(privateMatchScoreRepository).save(argThat(score -> {
            assertThat(score.getScore()).isEqualTo(86);
            assertThat(score.getScoreReason()).isEqualTo("기술스택 일치");
            assertThat(score.getPrivateJobPosting()).isEqualTo(posting);
            return true;
        }));
    }

    @Test
    @DisplayName("변경된 공고(posting.updatedAt > score.createdAt)는 기존 점수 삭제 후 재산출한다")
    void scoreNewAndUpdatedPostings_변경공고_재산출() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));

        LocalDateTime scoreCreatedAt = LocalDateTime.of(2026, 7, 1, 2, 0);
        LocalDateTime postingUpdatedAt = LocalDateTime.of(2026, 7, 5, 10, 0);
        PrivateJobPosting posting = createPosting("백엔드 개발자", "백엔드", postingUpdatedAt);
        when(privateJobPostingRepository.findActiveByValidCategories(VALID_CATEGORIES))
                .thenReturn(List.of(posting));

        PrivateMatchScore existingScore = createExistingScore(resume, posting, scoreCreatedAt);
        when(privateMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of(existingScore));

        stubEmbedding(posting);
        when(aiScoringClient.scorePrivate(any()))
                .thenReturn(Mono.just(dummyScoreResponse(90.0)));

        service.scoreNewAndUpdatedPostings();

        // 기존 점수 삭제 후 재산출
        var inOrder = inOrder(privateMatchScoreRepository);
        inOrder.verify(privateMatchScoreRepository).delete(existingScore);
        inOrder.verify(privateMatchScoreRepository).flush();
        inOrder.verify(privateMatchScoreRepository).save(argThat(score -> {
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
        PrivateJobPosting posting = createPosting("백엔드 개발자", "백엔드", postingUpdatedAt);

        PrivateMatchScore existingScore = createExistingScore(resume, posting, scoreCreatedAt);
        when(privateMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of(existingScore));

        stubEmbedding(posting);
        when(aiScoringClient.scorePrivate(any()))
                .thenThrow(new RuntimeException("AI 서버 타임아웃"));

        Map<Long, PrivateJobPosting> postingMap = Map.of(posting.getId(), posting);

        // 변경 재산출 분기에서 예외가 전파되어야 한다 (트랜잭션 롤백 → delete도 롤백)
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.scoreForResume(resume, postingMap));
    }

    @Test
    @DisplayName("기존 점수가 있고 변경 없는 공고는 건너뛴다")
    void scoreNewAndUpdatedPostings_변경없음_스킵() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));

        LocalDateTime scoreCreatedAt = LocalDateTime.of(2026, 7, 5, 2, 0);
        LocalDateTime postingUpdatedAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        PrivateJobPosting posting = createPosting("백엔드 개발자", "백엔드", postingUpdatedAt);
        when(privateJobPostingRepository.findActiveByValidCategories(VALID_CATEGORIES))
                .thenReturn(List.of(posting));

        PrivateMatchScore existingScore = createExistingScore(resume, posting, scoreCreatedAt);
        when(privateMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of(existingScore));

        service.scoreNewAndUpdatedPostings();

        verifyNoInteractions(aiScoringClient);
        verify(privateMatchScoreRepository, never()).save(any());
        verify(privateMatchScoreRepository, never()).delete(any());
    }

    @Test
    @DisplayName("한 공고 점수 산출 실패 시에도 나머지 공고는 계속 처리된다")
    void scoreNewAndUpdatedPostings_부분실패_공고() {
        Member member = createMember("신입");
        Resumes resume = createResume(1L, member, dummyEmbedding(), "[\"Java\"]");
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));

        PrivateJobPosting posting1 = createPosting("공고1", "백엔드", LocalDateTime.now());
        PrivateJobPosting posting2 = createPosting("공고2", "프론트엔드", LocalDateTime.now());
        when(privateJobPostingRepository.findActiveByValidCategories(VALID_CATEGORIES))
                .thenReturn(List.of(posting1, posting2));
        when(privateMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of());

        // posting1: 임베딩 없음 → 생성 실패
        when(jobEmbeddingRepository.findBySourceAndSourceId(JobSource.PRIVATE, posting1.getId()))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("AI 서버 오류"))
                .when(embeddingService).embedPrivatePosting(posting1);

        // posting2: 정상
        stubEmbedding(posting2);
        when(aiScoringClient.scorePrivate(any()))
                .thenReturn(Mono.just(dummyScoreResponse(75.0)));

        service.scoreNewAndUpdatedPostings();

        // posting1 실패해도 posting2는 정상 저장
        verify(privateMatchScoreRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("한 이력서 처리 실패 시에도 나머지 이력서는 계속 처리된다")
    void scoreNewAndUpdatedPostings_부분실패_이력서() {
        Member member1 = createMember("신입");
        Member member2 = createMember("경력");
        Resumes resume1 = createResume(1L, member1, dummyEmbedding(), "[\"Java\"]");
        Resumes resume2 = createResume(2L, member2, dummyEmbedding(), "[\"Python\"]");
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume1, resume2));

        PrivateJobPosting posting = createPosting("개발자", "백엔드", LocalDateTime.now());
        when(privateJobPostingRepository.findActiveByValidCategories(VALID_CATEGORIES))
                .thenReturn(List.of(posting));

        // resume1: findByResumeId 에서 예외 발생
        when(privateMatchScoreRepository.findByResumeId(1L))
                .thenThrow(new RuntimeException("DB 오류"));

        // resume2: 정상
        when(privateMatchScoreRepository.findByResumeId(2L)).thenReturn(List.of());
        stubEmbedding(posting);
        when(aiScoringClient.scorePrivate(any()))
                .thenReturn(Mono.just(dummyScoreResponse(80.0)));

        service.scoreNewAndUpdatedPostings();

        // resume1 실패해도 resume2는 정상 저장
        verify(privateMatchScoreRepository, times(1)).save(any());
    }
}

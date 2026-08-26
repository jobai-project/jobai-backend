package com.jobai.backend.domain.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import com.jobai.backend.global.enums.JobCategory;
import com.jobai.backend.global.enums.JobSource;
import com.jobai.backend.global.kafka.event.ScoringRequestEvent;
import com.jobai.backend.global.kafka.producer.KafkaScoringProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScoringDispatcherTest {

    private ResumesRepository resumesRepository;
    private PrivateJobPostingRepository privateJobPostingRepository;
    private PrivateMatchScoreRepository privateMatchScoreRepository;
    private JobEmbeddingRepository jobEmbeddingRepository;
    private NotificationRepository notificationRepository;
    private KafkaScoringProducer kafkaScoringProducer;
    private StringRedisTemplate stringRedisTemplate;
    private ObjectMapper objectMapper;

    private ScoringDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        resumesRepository = Mockito.mock(ResumesRepository.class);
        privateJobPostingRepository = Mockito.mock(PrivateJobPostingRepository.class);
        privateMatchScoreRepository = Mockito.mock(PrivateMatchScoreRepository.class);
        jobEmbeddingRepository = Mockito.mock(JobEmbeddingRepository.class);
        notificationRepository = Mockito.mock(NotificationRepository.class);
        kafkaScoringProducer = Mockito.mock(KafkaScoringProducer.class);
        stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();

        dispatcher = new ScoringDispatcher(
                resumesRepository,
                privateJobPostingRepository,
                privateMatchScoreRepository,
                jobEmbeddingRepository,
                notificationRepository,
                kafkaScoringProducer,
                stringRedisTemplate,
                objectMapper
        );
    }

    @Test
    @DisplayName("활성 이력서가 없으면 이벤트 발행 없이 0건 반환")
    void dispatchPrivateScoring_활성이력서없음_0건반환() {
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of());

        ScoringDispatcher.DispatchResult result = dispatcher.dispatchPrivateScoring();

        assertThat(result.dispatched()).isZero();
        assertThat(result.pipelineRunId()).isNull();
        verify(kafkaScoringProducer, never()).send(any());
    }

    @Test
    @DisplayName("활성 공고가 없으면 이벤트 발행 없이 0건 반환")
    void dispatchPrivateScoring_활성공고없음_0건반환() {
        Resumes resume = createMockResume(1L, "test@test.com");
        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));
        when(privateJobPostingRepository.findActiveByValidCategories(anyList())).thenReturn(List.of());

        ScoringDispatcher.DispatchResult result = dispatcher.dispatchPrivateScoring();

        assertThat(result.dispatched()).isZero();
        assertThat(result.pipelineRunId()).isNull();
        verify(kafkaScoringProducer, never()).send(any());
    }

    @Test
    @DisplayName("이미 점수가 존재하는 조합은 스킵한다")
    void dispatchPrivateScoring_기존점수존재_스킵() {
        Resumes resume = createMockResume(1L, "test@test.com");
        PrivateJobPosting posting = createMockPosting(100L);
        JobEmbedding embedding = createMockEmbedding(100L);
        PrivateMatchScore existingScore = Mockito.mock(PrivateMatchScore.class);
        PrivateJobPosting scorePosting = Mockito.mock(PrivateJobPosting.class);

        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));
        when(privateJobPostingRepository.findActiveByValidCategories(anyList())).thenReturn(List.of(posting));
        when(jobEmbeddingRepository.findBySourceAndSourceIdIn(eq(JobSource.PRIVATE), anyList()))
                .thenReturn(List.of(embedding));
        when(notificationRepository.findByMemberEmail("test@test.com")).thenReturn(Optional.empty());
        // 기존 점수 존재
        when(scorePosting.getId()).thenReturn(100L);
        when(existingScore.getPrivateJobPosting()).thenReturn(scorePosting);
        when(privateMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of(existingScore));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        ScoringDispatcher.DispatchResult result = dispatcher.dispatchPrivateScoring();

        assertThat(result.dispatched()).isZero();
        verify(kafkaScoringProducer, never()).send(any());
    }

    @Test
    @DisplayName("임베딩이 없는 공고는 스킵한다")
    void dispatchPrivateScoring_임베딩없음_스킵() {
        Resumes resume = createMockResume(1L, "test@test.com");
        PrivateJobPosting posting = createMockPosting(100L);

        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));
        when(privateJobPostingRepository.findActiveByValidCategories(anyList())).thenReturn(List.of(posting));
        when(jobEmbeddingRepository.findBySourceAndSourceIdIn(eq(JobSource.PRIVATE), anyList()))
                .thenReturn(List.of()); // 임베딩 없음
        when(notificationRepository.findByMemberEmail("test@test.com")).thenReturn(Optional.empty());
        when(privateMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        ScoringDispatcher.DispatchResult result = dispatcher.dispatchPrivateScoring();

        assertThat(result.dispatched()).isZero();
        verify(kafkaScoringProducer, never()).send(any());
    }

    @Test
    @DisplayName("신규 조합은 ScoringRequestEvent로 발행되고 Redis에 total 저장")
    void dispatchPrivateScoring_신규조합_이벤트발행() {
        Resumes resume = createMockResume(1L, "test@test.com");
        PrivateJobPosting posting = createMockPosting(100L);
        JobEmbedding embedding = createMockEmbedding(100L);

        when(resumesRepository.findAllActiveWithEmbedding()).thenReturn(List.of(resume));
        when(privateJobPostingRepository.findActiveByValidCategories(anyList())).thenReturn(List.of(posting));
        when(jobEmbeddingRepository.findBySourceAndSourceIdIn(eq(JobSource.PRIVATE), anyList()))
                .thenReturn(List.of(embedding));
        when(notificationRepository.findByMemberEmail("test@test.com")).thenReturn(Optional.empty());
        when(privateMatchScoreRepository.findByResumeId(1L)).thenReturn(List.of()); // 기존 점수 없음

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        ScoringDispatcher.DispatchResult result = dispatcher.dispatchPrivateScoring();

        assertThat(result.dispatched()).isEqualTo(1);
        assertThat(result.pipelineRunId()).isNotNull();

        // 이벤트 발행 검증
        ArgumentCaptor<ScoringRequestEvent> captor = ArgumentCaptor.forClass(ScoringRequestEvent.class);
        verify(kafkaScoringProducer).send(captor.capture());
        ScoringRequestEvent event = captor.getValue();
        assertThat(event.resumeId()).isEqualTo(1L);
        assertThat(event.postingId()).isEqualTo(100L);
        assertThat(event.userEmail()).isEqualTo("test@test.com");

        // Redis에 total, startMs 저장 검증
        verify(valueOps).set(contains(":total"), eq("1"), any());
        verify(valueOps).set(contains(":startMs"), anyString(), any());
    }

    // --- 헬퍼 메서드 ---

    private Resumes createMockResume(Long id, String email) {
        Member member = Mockito.mock(Member.class);
        when(member.getEmail()).thenReturn(email);
        when(member.getId()).thenReturn(1L);

        Resumes resume = Mockito.mock(Resumes.class);
        when(resume.getId()).thenReturn(id);
        when(resume.getMember()).thenReturn(member);
        when(resume.getEmbedding()).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(resume.getResumeSkills()).thenReturn("[\"Java\",\"Spring\"]");
        when(resume.getExperienceYears()).thenReturn(3);
        return resume;
    }

    private PrivateJobPosting createMockPosting(Long id) {
        PrivateJobPosting posting = Mockito.mock(PrivateJobPosting.class);
        when(posting.getId()).thenReturn(id);
        when(posting.getTitle()).thenReturn("백엔드 개발자");
        when(posting.getDescription()).thenReturn("Java/Spring 경력자 모집");
        when(posting.getCompany()).thenReturn("테스트회사");
        when(posting.getLocation()).thenReturn("서울");
        when(posting.getEmploymentType()).thenReturn("정규직");
        when(posting.getJobCategory()).thenReturn("백엔드");
        when(posting.getDeadline()).thenReturn(null);
        return posting;
    }

    private JobEmbedding createMockEmbedding(Long sourceId) {
        JobEmbedding embedding = Mockito.mock(JobEmbedding.class);
        when(embedding.getSourceId()).thenReturn(sourceId);
        when(embedding.getEmbedding()).thenReturn(new float[]{0.4f, 0.5f, 0.6f});
        return embedding;
    }
}

package com.jobai.backend.global.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.service.BatchNotificationHelper;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.global.ai.client.AiScoringClient;
import com.jobai.backend.global.ai.dto.ScorePrivateResponse;
import com.jobai.backend.global.kafka.event.ScoringRequestEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaScoringConsumerTest {

    private AiScoringClient aiScoringClient;
    private PrivateMatchScoreRepository privateMatchScoreRepository;
    private PrivateJobPostingRepository privateJobPostingRepository;
    private ResumesRepository resumesRepository;
    private MemberRepository memberRepository;
    private ObjectMapper objectMapper;
    private StringRedisTemplate stringRedisTemplate;
    private BatchNotificationHelper batchNotificationHelper;

    private KafkaScoringConsumer consumer;

    @BeforeEach
    void setUp() {
        aiScoringClient = Mockito.mock(AiScoringClient.class);
        privateMatchScoreRepository = Mockito.mock(PrivateMatchScoreRepository.class);
        privateJobPostingRepository = Mockito.mock(PrivateJobPostingRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        memberRepository = Mockito.mock(MemberRepository.class);
        objectMapper = new ObjectMapper();
        stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        batchNotificationHelper = Mockito.mock(BatchNotificationHelper.class);

        consumer = new KafkaScoringConsumer(
                aiScoringClient,
                privateMatchScoreRepository,
                privateJobPostingRepository,
                resumesRepository,
                memberRepository,
                objectMapper,
                stringRedisTemplate,
                batchNotificationHelper
        );
    }

    @Test
    @DisplayName("이미 점수가 존재하면 AI 호출 없이 스킵한다 (멱등성)")
    void consume_이미점수존재_스킵() {
        ScoringRequestEvent event = createEvent(70);
        when(privateMatchScoreRepository.existsByResumeIdAndPrivateJobPostingId(1L, 100L))
                .thenReturn(true);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        consumer.consume(event);

        verify(aiScoringClient, never()).scorePrivate(any());
        verify(privateMatchScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 처리 시 AI 호출 → DB 저장 → Redis 카운터 증가")
    void consume_정상처리_AI호출_DB저장_Redis증가() {
        ScoringRequestEvent event = createEvent(70);
        when(privateMatchScoreRepository.existsByResumeIdAndPrivateJobPostingId(1L, 100L))
                .thenReturn(false);

        ScorePrivateResponse response = new ScorePrivateResponse(
                65.0, false, List.of("Java"), List.of("Kotlin"), true, "Good fit", "v1"
        );
        when(aiScoringClient.scorePrivate(any())).thenReturn(Mono.just(response));
        when(privateJobPostingRepository.getReferenceById(100L))
                .thenReturn(Mockito.mock(PrivateJobPosting.class));
        when(resumesRepository.getReferenceById(1L))
                .thenReturn(Mockito.mock(Resumes.class));
        when(memberRepository.getReferenceById(1L))
                .thenReturn(Mockito.mock(Member.class));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        consumer.consume(event);

        // AI 호출 검증
        verify(aiScoringClient).scorePrivate(any());
        // DB 저장 검증
        verify(privateMatchScoreRepository).save(any(PrivateMatchScore.class));
        // Redis 카운터 증가 검증
        verify(valueOps).increment(contains(":completed"));
    }

    @Test
    @DisplayName("전체 완료 시 배치 알림이 1회 발송된다")
    void consume_전체완료시_배치알림발송() {
        ScoringRequestEvent event = createEvent(60);
        when(privateMatchScoreRepository.existsByResumeIdAndPrivateJobPostingId(1L, 100L))
                .thenReturn(false);

        ScorePrivateResponse response = new ScorePrivateResponse(
                75.0, true, List.of("Java"), List.of(), true, "Great fit", "v1"
        );
        when(aiScoringClient.scorePrivate(any())).thenReturn(Mono.just(response));
        when(privateJobPostingRepository.getReferenceById(100L))
                .thenReturn(Mockito.mock(PrivateJobPosting.class));
        when(resumesRepository.getReferenceById(1L))
                .thenReturn(Mockito.mock(Resumes.class));
        when(memberRepository.getReferenceById(1L))
                .thenReturn(Mockito.mock(Member.class));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        // completed(1) >= total(1) → 완료 판정
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(valueOps.get(contains(":total"))).thenReturn("1");
        when(valueOps.get(contains(":startMs"))).thenReturn(String.valueOf(System.currentTimeMillis()));
        when(valueOps.get(contains(":failed"))).thenReturn(null);
        // setIfAbsent → true (최초 스레드)
        when(valueOps.setIfAbsent(contains(":result"), anyString(), any())).thenReturn(true);

        consumer.consume(event);

        // 배치 알림 발송 검증
        verify(batchNotificationHelper).sendNotificationsForExistingScores();
    }

    @Test
    @DisplayName("개별 알림은 발행하지 않는다 (배치 알림으로 통일)")
    void consume_개별알림미발행() {
        ScoringRequestEvent event = createEvent(60);
        when(privateMatchScoreRepository.existsByResumeIdAndPrivateJobPostingId(1L, 100L))
                .thenReturn(false);

        ScorePrivateResponse response = new ScorePrivateResponse(
                75.0, true, List.of("Java"), List.of(), true, "Great fit", "v1"
        );
        when(aiScoringClient.scorePrivate(any())).thenReturn(Mono.just(response));
        when(privateJobPostingRepository.getReferenceById(100L))
                .thenReturn(Mockito.mock(PrivateJobPosting.class));
        when(resumesRepository.getReferenceById(1L))
                .thenReturn(Mockito.mock(Resumes.class));
        when(memberRepository.getReferenceById(1L))
                .thenReturn(Mockito.mock(Member.class));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        consumer.consume(event);

        // 점수가 임계값 이상이더라도 개별 알림은 발행하지 않음
        // (배치 알림으로 통일하므로 KafkaNotificationProducer 의존성 자체가 없음)
        verify(privateMatchScoreRepository).save(any(PrivateMatchScore.class));
    }

    @Test
    @DisplayName("AI 응답이 null이면 예외가 발생한다")
    void consume_AI응답null_예외발생() {
        ScoringRequestEvent event = createEvent(70);
        when(privateMatchScoreRepository.existsByResumeIdAndPrivateJobPostingId(1L, 100L))
                .thenReturn(false);
        when(aiScoringClient.scorePrivate(any())).thenReturn(Mono.empty());

        // finally에서 trackCompletion이 호출되므로 Redis mock 필요
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 점수 응답 null");
    }

    // --- 헬퍼 메서드 ---

    private ScoringRequestEvent createEvent(int scoreThreshold) {
        return new ScoringRequestEvent(
                "test-pipeline-run-id",
                1L,       // resumeId
                1L,       // memberId
                "test@test.com",
                100L,     // postingId
                "PRIVATE",
                "백엔드 개발자\nJava/Spring 경력자 모집",
                List.of(0.4, 0.5, 0.6),  // jdVector
                List.of(0.1, 0.2, 0.3),  // resumeVector
                List.of("Java", "Spring"),
                3,        // experienceYears
                scoreThreshold,
                "백엔드 개발자",
                "테스트회사",
                "서울",
                "정규직",
                "백엔드",
                null      // deadline
        );
    }
}

package com.jobai.backend.domain.matching.service;

import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.notification.service.NotificationDispatchService;
import com.jobai.backend.domain.notification.service.NotificationMatchBatchService;
import com.jobai.backend.global.kafka.event.NotificationDispatchEvent;
import com.jobai.backend.global.kafka.producer.KafkaNotificationProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BatchNotificationHelperTest {

    private NotificationDispatchService notificationDispatchService;
    private ResumesRepository resumesRepository;
    private PrivateMatchScoreRepository privateMatchScoreRepository;
    private PublicMatchScoreRepository publicMatchScoreRepository;
    private NotificationRepository notificationRepository;
    private NotificationMatchBatchService notificationMatchBatchService;
    private KafkaNotificationProducer kafkaNotificationProducer;

    @BeforeEach
    void setUp() {
        notificationDispatchService = Mockito.mock(NotificationDispatchService.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);
        privateMatchScoreRepository = Mockito.mock(PrivateMatchScoreRepository.class);
        publicMatchScoreRepository = Mockito.mock(PublicMatchScoreRepository.class);
        notificationRepository = Mockito.mock(NotificationRepository.class);
        notificationMatchBatchService = Mockito.mock(NotificationMatchBatchService.class);
        kafkaNotificationProducer = Mockito.mock(KafkaNotificationProducer.class);
    }

    private BatchNotificationHelper createHelper(boolean kafkaEnabled, KafkaNotificationProducer producer) {
        @SuppressWarnings("unchecked")
        ObjectProvider<KafkaNotificationProducer> producerProvider = Mockito.mock(ObjectProvider.class);
        when(producerProvider.getIfAvailable()).thenReturn(producer);

        return new BatchNotificationHelper(
                notificationDispatchService,
                resumesRepository,
                privateMatchScoreRepository,
                publicMatchScoreRepository,
                notificationRepository,
                notificationMatchBatchService,
                producerProvider,
                kafkaEnabled
        );
    }

    @Test
    @DisplayName("kafkaNotificationEnabled=true + producer 존재 → Kafka 발행")
    void sendIfNeeded_kafka활성_producer존재_kafka발행() {
        BatchNotificationHelper helper = createHelper(true, kafkaNotificationProducer);
        Member member = createMockMember();
        List<BatchNotificationHelper.ScoredPosting> postings = List.of(createScoredPosting(85));

        when(notificationMatchBatchService.create(any(), any(), anyList())).thenReturn(1L);

        helper.sendIfNeeded(member, postings, "새 추천 공고");

        // Kafka 발행 검증
        ArgumentCaptor<NotificationDispatchEvent> captor =
                ArgumentCaptor.forClass(NotificationDispatchEvent.class);
        verify(kafkaNotificationProducer).send(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("test@test.com");

        // 직접 발송 미호출 검증
        verify(notificationDispatchService, never()).notifyUser(anyString(), any());
    }

    @Test
    @DisplayName("kafkaNotificationEnabled=false → 직접 발송")
    void sendIfNeeded_kafka비활성_직접발송() {
        BatchNotificationHelper helper = createHelper(false, kafkaNotificationProducer);
        Member member = createMockMember();
        List<BatchNotificationHelper.ScoredPosting> postings = List.of(createScoredPosting(85));

        when(notificationMatchBatchService.create(any(), any(), anyList())).thenReturn(1L);

        helper.sendIfNeeded(member, postings, "새 추천 공고");

        // 직접 발송 검증
        verify(notificationDispatchService).notifyUser(eq("test@test.com"), any(RealtimeNotificationPayload.class));
        // Kafka 미발행 검증
        verify(kafkaNotificationProducer, never()).send(any());
    }

    @Test
    @DisplayName("kafkaNotificationEnabled=true + producer 없음 → 직접 발송 (fallback)")
    void sendIfNeeded_kafka활성_producer없음_직접발송() {
        BatchNotificationHelper helper = createHelper(true, null); // producer 없음
        Member member = createMockMember();
        List<BatchNotificationHelper.ScoredPosting> postings = List.of(createScoredPosting(85));

        when(notificationMatchBatchService.create(any(), any(), anyList())).thenReturn(1L);

        helper.sendIfNeeded(member, postings, "새 추천 공고");

        // 직접 발송 검증 (fallback)
        verify(notificationDispatchService).notifyUser(eq("test@test.com"), any(RealtimeNotificationPayload.class));
    }

    @Test
    @DisplayName("빈 리스트 전달 시 아무것도 호출하지 않는다")
    void sendIfNeeded_빈리스트_호출없음() {
        BatchNotificationHelper helper = createHelper(false, null);
        Member member = createMockMember();

        helper.sendIfNeeded(member, List.of(), "새 추천 공고");

        verify(notificationMatchBatchService, never()).create(any(), any(), anyList());
        verify(notificationDispatchService, never()).notifyUser(anyString(), any());
        verify(kafkaNotificationProducer, never()).send(any());
    }

    // --- 헬퍼 메서드 ---

    private Member createMockMember() {
        Member member = Mockito.mock(Member.class);
        when(member.getEmail()).thenReturn("test@test.com");
        return member;
    }

    private BatchNotificationHelper.ScoredPosting createScoredPosting(int score) {
        return new BatchNotificationHelper.ScoredPosting(
                "PRIVATE", "백엔드 개발자", "테스트회사", score,
                100L, "/jobs/private/", "서울", "정규직", "백엔드", LocalDate.now()
        );
    }
}

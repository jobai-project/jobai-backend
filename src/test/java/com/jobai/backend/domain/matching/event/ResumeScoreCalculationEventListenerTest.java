package com.jobai.backend.domain.matching.event;

import com.jobai.backend.domain.matching.service.PrivateMatchingService;
import com.jobai.backend.domain.matching.service.PublicMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

class ResumeScoreCalculationEventListenerTest {

    private PrivateMatchingService privateMatchingService;
    private PublicMatchingService publicMatchingService;
    private ResumeScoreCalculationEventListener listener;

    @BeforeEach
    void setUp() {
        privateMatchingService = Mockito.mock(PrivateMatchingService.class);
        publicMatchingService = Mockito.mock(PublicMatchingService.class);
        listener = new ResumeScoreCalculationEventListener(privateMatchingService, publicMatchingService);
    }

    @Test
    @DisplayName("커밋 후 이벤트를 받으면 공기업과 민간 매칭점수를 모두 계산한다")
    void calculatesBothScores() {
        listener.calculateScores(new ResumeScoreCalculationRequestedEvent(10L));

        verify(privateMatchingService).calculateScores(10L);
        verify(publicMatchingService).calculateScores(10L);
    }

    @Test
    @DisplayName("민간 점수 계산이 실패해도 공기업 점수 계산을 계속한다")
    void publicCalculationContinuesAfterPrivateFailure() {
        doThrow(new IllegalStateException("AI unavailable"))
                .when(privateMatchingService).calculateScores(10L);

        listener.calculateScores(new ResumeScoreCalculationRequestedEvent(10L));

        verify(publicMatchingService).calculateScores(10L);
    }

    @Test
    @DisplayName("점수 계산 이벤트는 이력서 트랜잭션 커밋 이후에만 처리한다")
    void handlesEventAfterCommit() throws NoSuchMethodException {
        TransactionalEventListener annotation = ResumeScoreCalculationEventListener.class
                .getMethod("calculateScores", ResumeScoreCalculationRequestedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}

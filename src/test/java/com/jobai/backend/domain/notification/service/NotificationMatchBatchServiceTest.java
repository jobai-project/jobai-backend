package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.notification.dto.NotificationMatchBatchResponse;
import com.jobai.backend.domain.notification.entity.NotificationMatchBatch;
import com.jobai.backend.domain.notification.entity.NotificationMatchBatchItem;
import com.jobai.backend.domain.notification.repository.NotificationMatchBatchRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationMatchBatchServiceTest {

    private NotificationMatchBatchRepository notificationMatchBatchRepository;
    private NotificationMatchBatchService notificationMatchBatchService;

    @BeforeEach
    void setUp() {
        notificationMatchBatchRepository = mock(NotificationMatchBatchRepository.class);
        notificationMatchBatchService = new NotificationMatchBatchService(notificationMatchBatchRepository);
    }

    @Test
    @DisplayName("추천 알림 묶음 저장 시 공고 표시 순서를 1부터 부여한다")
    void create_공고표시순서_부여() {
        Member member = member("owner@example.com");
        List<NotificationMatchBatchService.BatchItemCommand> items = List.of(
                item("PRIVATE", 1L, "첫 번째 공고", 91),
                item("PUBLIC", 2L, "두 번째 공고", 88)
        );
        when(notificationMatchBatchRepository.save(any(NotificationMatchBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationMatchBatchService.create(member, "새 추천 공고", items);

        ArgumentCaptor<NotificationMatchBatch> captor = ArgumentCaptor.forClass(NotificationMatchBatch.class);
        verify(notificationMatchBatchRepository).save(captor.capture());

        NotificationMatchBatch saved = captor.getValue();
        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.getNotificationType()).isEqualTo("새 추천 공고");
        assertThat(saved.getItemCount()).isEqualTo(2);
        assertThat(saved.getItems())
                .extracting(NotificationMatchBatchItem::getDisplayOrder)
                .containsExactly(1, 2);
        assertThat(saved.getItems())
                .extracting(NotificationMatchBatchItem::getTitle)
                .containsExactly("첫 번째 공고", "두 번째 공고");
    }

    @Test
    @DisplayName("추천 알림 묶음 조회 시 표시 순서대로 공고를 반환한다")
    void getMatchBatch_표시순서대로_조회() {
        NotificationMatchBatch batch = NotificationMatchBatch.builder()
                .member(member("owner@example.com"))
                .notificationType("새 추천 공고")
                .itemCount(2)
                .build();
        batch.addItem(batchItem(2, "PRIVATE", 2L, "두 번째 공고", 82));
        batch.addItem(batchItem(1, "PUBLIC", 1L, "첫 번째 공고", 93));

        when(notificationMatchBatchRepository.findWithItemsById(10L)).thenReturn(Optional.of(batch));

        NotificationMatchBatchResponse response = notificationMatchBatchService.getMatchBatch("owner@example.com", 10L);

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.jobs())
                .extracting(NotificationMatchBatchResponse.RecommendedJob::title)
                .containsExactly("첫 번째 공고", "두 번째 공고");
    }

    @Test
    @DisplayName("타인 추천 알림 묶음 조회 시 존재하지 않는 것처럼 응답한다")
    void getMatchBatch_타인소유_조회불가() {
        NotificationMatchBatch batch = NotificationMatchBatch.builder()
                .member(member("owner@example.com"))
                .notificationType("새 추천 공고")
                .itemCount(1)
                .build();
        when(notificationMatchBatchRepository.findWithItemsById(10L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> notificationMatchBatchService.getMatchBatch("other@example.com", 10L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GeneralErrorCode.NOT_FOUND));
    }

    private Member member(String email) {
        return Member.builder()
                .email(email)
                .build();
    }

    private NotificationMatchBatchService.BatchItemCommand item(String source, Long jobId, String title, int score) {
        return new NotificationMatchBatchService.BatchItemCommand(
                source,
                jobId,
                title,
                "테스트 회사",
                "서울",
                "정규직",
                LocalDate.of(2026, 8, 1),
                score,
                "/jobs/" + source.toLowerCase() + "/" + jobId
        );
    }

    private NotificationMatchBatchItem batchItem(int displayOrder, String source, Long jobId, String title, int score) {
        return NotificationMatchBatchItem.builder()
                .displayOrder(displayOrder)
                .source(source)
                .jobId(jobId)
                .title(title)
                .companyName("테스트 회사")
                .location("서울")
                .employmentType("정규직")
                .deadline(LocalDate.of(2026, 8, 1))
                .matchScore(score)
                .detailLinkUrl("/jobs/" + source.toLowerCase() + "/" + jobId)
                .build();
    }
}

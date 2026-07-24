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
    @DisplayName("notification match batch items get display order from 1")
    void create_assignsDisplayOrder() {
        Member member = member("owner@example.com");
        List<NotificationMatchBatchService.BatchItemCommand> items = List.of(
                item("PRIVATE", 1L, "First Job", 91),
                item("PUBLIC", 2L, "Second Job", 88)
        );
        when(notificationMatchBatchRepository.save(any(NotificationMatchBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationMatchBatchService.create(member, "New Recommended Jobs", items);

        ArgumentCaptor<NotificationMatchBatch> captor = ArgumentCaptor.forClass(NotificationMatchBatch.class);
        verify(notificationMatchBatchRepository).save(captor.capture());

        NotificationMatchBatch saved = captor.getValue();
        assertThat(saved.getMember()).isEqualTo(member);
        assertThat(saved.getNotificationType()).isEqualTo("New Recommended Jobs");
        assertThat(saved.getItemCount()).isEqualTo(2);
        assertThat(saved.getItems())
                .extracting(NotificationMatchBatchItem::getDisplayOrder)
                .containsExactly(1, 2);
        assertThat(saved.getItems())
                .extracting(NotificationMatchBatchItem::getJobCategory)
                .containsExactly("Backend", "Backend");
    }

    @Test
    @DisplayName("get match batch returns jobs by display order")
    void getMatchBatch_returnsItemsByDisplayOrder() {
        NotificationMatchBatch batch = NotificationMatchBatch.builder()
                .member(member("owner@example.com"))
                .notificationType("New Recommended Jobs")
                .itemCount(2)
                .build();
        batch.addItem(batchItem(2, "PRIVATE", 2L, "Second Job", 82));
        batch.addItem(batchItem(1, "PUBLIC", 1L, "First Job", 93));

        when(notificationMatchBatchRepository.findWithItemsById(10L)).thenReturn(Optional.of(batch));

        NotificationMatchBatchResponse response = notificationMatchBatchService.getMatchBatch("owner@example.com", 10L);

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.jobs())
                .extracting(NotificationMatchBatchResponse.RecommendedJob::title)
                .containsExactly("First Job", "Second Job");
        assertThat(response.jobs())
                .extracting(NotificationMatchBatchResponse.RecommendedJob::jobCategory)
                .containsExactly("Backend", "Backend");
    }

    @Test
    @DisplayName("get match batch hides ownership mismatch as not found")
    void getMatchBatch_ownershipMismatch_returnsNotFound() {
        NotificationMatchBatch batch = NotificationMatchBatch.builder()
                .member(member("owner@example.com"))
                .notificationType("New Recommended Jobs")
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
                "Test Company",
                "Seoul",
                "Full-time",
                "Backend",
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
                .companyName("Test Company")
                .location("Seoul")
                .employmentType("Full-time")
                .jobCategory("Backend")
                .deadline(LocalDate.of(2026, 8, 1))
                .matchScore(score)
                .detailLinkUrl("/jobs/" + source.toLowerCase() + "/" + jobId)
                .build();
    }
}

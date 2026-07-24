package com.jobai.backend.domain.notification.dto;

import com.jobai.backend.domain.notification.entity.NotificationMatchBatch;
import com.jobai.backend.domain.notification.entity.NotificationMatchBatchItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Schema(name = "NotificationMatchBatchResponse")
public record NotificationMatchBatchResponse(
        Long batchId,
        String notificationType,
        Integer count,
        LocalDateTime createdAt,
        List<RecommendedJob> jobs
) {
    public static NotificationMatchBatchResponse from(NotificationMatchBatch batch) {
        List<RecommendedJob> jobs = batch.getItems().stream()
                .sorted(Comparator.comparing(NotificationMatchBatchItem::getDisplayOrder))
                .map(RecommendedJob::from)
                .toList();

        return new NotificationMatchBatchResponse(
                batch.getId(),
                batch.getNotificationType(),
                batch.getItemCount(),
                batch.getCreatedAt(),
                jobs
        );
    }

    @Schema(name = "NotificationMatchBatchJob")
    public record RecommendedJob(
            String source,
            Long jobId,
            String title,
            String companyName,
            String location,
            String employmentType,
            String jobCategory,
            LocalDate deadline,
            Integer matchScore,
            String detailLinkUrl
    ) {
        private static RecommendedJob from(NotificationMatchBatchItem item) {
            return new RecommendedJob(
                    item.getSource(),
                    item.getJobId(),
                    item.getTitle(),
                    item.getCompanyName(),
                    item.getLocation(),
                    item.getEmploymentType(),
                    item.getJobCategory(),
                    item.getDeadline(),
                    item.getMatchScore(),
                    item.getDetailLinkUrl()
            );
        }
    }
}

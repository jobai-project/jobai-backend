package com.jobai.backend.domain.notification.service;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.notification.dto.NotificationMatchBatchResponse;
import com.jobai.backend.domain.notification.entity.NotificationMatchBatch;
import com.jobai.backend.domain.notification.entity.NotificationMatchBatchItem;
import com.jobai.backend.domain.notification.repository.NotificationMatchBatchRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationMatchBatchService {

    private final NotificationMatchBatchRepository notificationMatchBatchRepository;

    @Transactional
    public Long create(Member member, String notificationType, List<BatchItemCommand> items) {
        NotificationMatchBatch batch = NotificationMatchBatch.builder()
                .member(member)
                .notificationType(notificationType)
                .itemCount(items.size())
                .build();

        for (int i = 0; i < items.size(); i++) {
            BatchItemCommand item = items.get(i);
            batch.addItem(NotificationMatchBatchItem.builder()
                    .displayOrder(i + 1)
                    .source(item.source())
                    .jobId(item.jobId())
                    .title(item.title())
                    .companyName(item.companyName())
                    .location(item.location())
                    .employmentType(item.employmentType())
                    .deadline(item.deadline())
                    .matchScore(item.matchScore())
                    .detailLinkUrl(item.detailLinkUrl())
                    .build());
        }

        return notificationMatchBatchRepository.save(batch).getId();
    }

    @Transactional(readOnly = true)
    public NotificationMatchBatchResponse getMatchBatch(String email, Long batchId) {
        NotificationMatchBatch batch = notificationMatchBatchRepository.findWithItemsById(batchId)
                .orElseThrow(this::matchBatchNotFound);

        if (!batch.getMember().getEmail().equals(email)) {
            throw matchBatchNotFound();
        }

        return NotificationMatchBatchResponse.from(batch);
    }

    private GeneralException matchBatchNotFound() {
        return new GeneralException(GeneralErrorCode.NOT_FOUND, "추천 알림 묶음을 찾을 수 없습니다.");
    }

    public record BatchItemCommand(
            String source,
            Long jobId,
            String title,
            String companyName,
            String location,
            String employmentType,
            LocalDate deadline,
            Integer matchScore,
            String detailLinkUrl
    ) {
    }
}

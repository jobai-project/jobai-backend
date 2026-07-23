package com.jobai.backend.domain.member.event;

import com.jobai.backend.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberWithdrawalFileCleanupListener {

    private final FileStorageService fileStorageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteResumeFiles(MemberWithdrawalCompletedEvent event) {
        for (String fileUrl : event.resumeFileUrls()) {
            try {
                fileStorageService.delete(fileUrl);
            } catch (RuntimeException e) {
                log.warn("Failed to delete withdrawn member resume file: fileUrl={}", fileUrl, e);
            }
        }
    }
}

package com.jobai.backend.domain.member.event;

import com.jobai.backend.global.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemberWithdrawalFileCleanupListenerTest {

    @Test
    void deletesFilesUsingSchedulerTaskExecutor() throws NoSuchMethodException {
        Method method = MemberWithdrawalFileCleanupListener.class.getMethod(
                "deleteResumeFiles", MemberWithdrawalCompletedEvent.class
        );

        Async async = method.getAnnotation(Async.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("schedulerTaskExecutor");
    }

    @Test
    void continuesDeletingOtherFilesWhenOneDeletionFails() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        MemberWithdrawalFileCleanupListener listener = new MemberWithdrawalFileCleanupListener(fileStorageService);
        doThrow(new RuntimeException("S3 unavailable")).when(fileStorageService).delete("https://bucket/first.pdf");

        listener.deleteResumeFiles(new MemberWithdrawalCompletedEvent(List.of(
                "https://bucket/first.pdf", "https://bucket/second.pdf"
        )));

        verify(fileStorageService).delete("https://bucket/first.pdf");
        verify(fileStorageService).delete("https://bucket/second.pdf");
    }
}

package com.jobai.backend.domain.notification.controller;

import com.jobai.backend.domain.notification.dto.NotificationMatchBatchResponse;
import com.jobai.backend.domain.notification.service.NotificationMatchBatchService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/matches")
@RequiredArgsConstructor
@Profile("!collect & !classify & !export")
public class NotificationMatchBatchController {

    private final NotificationMatchBatchService notificationMatchBatchService;

    @GetMapping("/{batchId}")
    public ApiResponse<NotificationMatchBatchResponse> getMatchBatch(
            @AuthenticationPrincipal String email,
            @PathVariable Long batchId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                notificationMatchBatchService.getMatchBatch(email, batchId)
        );
    }
}

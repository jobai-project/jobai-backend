package com.jobai.backend.domain.notification.controller;

import com.jobai.backend.domain.notification.LambdaTestRequest;
import com.jobai.backend.domain.notification.service.LambdaNotificationService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
@Profile("!collect & !classify & !export")
public class notificationController implements NotificationControllerDocs {

    private final LambdaNotificationService lambdaNotificationService;

    @PostMapping("/lambda-test")
    public ApiResponse<Void> triggerLambdaNotification(@RequestBody LambdaTestRequest request) {

        lambdaNotificationService.sendMatchNotification(
                request.userName(),
                request.companyName(),
                request.score()
        );

        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }
}

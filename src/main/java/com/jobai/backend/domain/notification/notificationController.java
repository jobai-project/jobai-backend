package com.jobai.backend.domain.notification;

import com.jobai.backend.domain.notification.service.LambdaNotificationService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 기능 테스트용 API")
@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
public class notificationController {

    private final LambdaNotificationService lambdaNotificationService;

    @PostMapping("/lambda-test")
    public ApiResponse<Void> triggerLambdaNotification(@RequestBody LambdaTestRequest request) {

        // 1. 주입받은 서비스의 Lambda 호출 로직 실행
        lambdaNotificationService.sendMatchNotification(
                request.userName(),
                request.companyName(),
                request.score()
        );

        // 2. 프로젝트 공통 응답 규격(COMMON_200_001)으로 반환
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }
}

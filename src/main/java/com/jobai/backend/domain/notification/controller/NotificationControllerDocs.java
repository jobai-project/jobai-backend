package com.jobai.backend.domain.notification.controller;

import com.jobai.backend.domain.notification.LambdaTestRequest;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notification", description = "알림 기능 API (현재 Lambda 테스트용)")
@SecurityRequirement(name = "cookieAuth")
public interface NotificationControllerDocs {

    @Operation(
            summary = "[테스트] AWS Lambda 알림 발송",
            description = """
                    AWS Lambda를 통해 매칭 알림을 비동기로 발송합니다.

                    > ⚠️ **현재는 테스트용 API입니다.** 실제 서비스에서는 자동으로 호출됩니다.

                    **인증 필요**: 로그인 후 발급된 accessToken 쿠키가 있어야 합니다.

                    **동작 방식**: Lambda 함수를 `EVENT` 방식(비동기)으로 호출합니다.
                    요청 즉시 202 응답을 반환하며, 실제 알림은 Lambda가 처리합니다.

                    **score 허용 범위**: 0 ~ 100 (정수)
                    """
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = LambdaTestRequest.class),
                    examples = @ExampleObject(
                            name = "Lambda 알림 발송 예시",
                            value = """
                                    {
                                      "userName": "홍길동",
                                      "companyName": "카카오",
                                      "score": 85
                                    }
                                    """
                    )
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lambda 이벤트 발송 요청 완료 (비동기 처리 — 실제 알림 발송은 Lambda에서 처리됨)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 (이름 누락, score 범위 초과 등)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_400_001",
                                      "message": "점수는 100점 이하이어야 합니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자 (로그인 필요)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_401_001",
                                      "message": "인증이 필요합니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<Void> triggerLambdaNotification(LambdaTestRequest request);
}

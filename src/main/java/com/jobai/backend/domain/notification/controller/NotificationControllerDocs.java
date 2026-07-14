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

@Tag(name = "Notification", description = "알림 기능 API")
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

    @Operation(
            summary = "[테스트] 점수 산출 + 알림 발송 수동 실행",
            description = """
                    스케줄러의 Step 4(사기업·공기업 매칭 점수 산출)를 수동으로 실행합니다.

                    > ⚠️ **테스트용 API입니다.** 실 서비스에서는 새벽 2시 스케줄러가 자동 실행합니다.

                    **동작 방식**:
                    1. 신규/변경된 공고에 대해 AI 매칭 점수를 산출합니다.
                    2. 사용자가 설정한 임계값(matchScoreThreshold) 이상인 공고가 있으면
                       WebSocket 실시간 알림 + Slack/Discord webhook 알림을 발송합니다.

                    **인증 불필요**: 배치 작업이므로 별도 인증 없이 호출 가능합니다.
                    처리 시간이 길 수 있습니다 (이력서 수 × 공고 수만큼 AI 호출).
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "점수 산출 + 알림 발송 완료",
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
            )
    })
    ApiResponse<Void> triggerScoreAndNotify();
}

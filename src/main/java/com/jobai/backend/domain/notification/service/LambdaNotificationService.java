package com.jobai.backend.domain.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.InvocationType;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!collect & !classify & !export")
public class LambdaNotificationService {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    public void sendMatchNotification(String userName, String companyName, int score) {
        try {
            // 1. Lambda로 전송할 JSON 페이로드 구조 생성 (Map 혹은 전용 DTO)
            Map<String, Object> payloadMap = Map.of(
                    "userName", userName,
                    "companyName", companyName,
                    "score", score
            );

            // 2. JSON 문자열로 직렬화 후 SdkBytes 바이트 배열로 래핑
            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            SdkBytes payload = SdkBytes.fromUtf8String(jsonPayload);

            // 3. InvokeRequest 생성
            InvokeRequest request = InvokeRequest.builder()
                    .functionName("jobai-notification-test") // 대시보드에서 만든 Lambda 함수명
                    .invocationType(InvocationType.EVENT)    // EVENT: 비동기 호출 (스프링은 바로 리턴받음)
                    // REQUEST_RESPONSE: 동기 호출 (결과를 기다림)
                    .payload(payload)
                    .build();

            // 4. Lambda 호출 실행
            InvokeResponse response = lambdaClient.invoke(request);

            // InvocationType.EVENT인 경우 성공 시 202(Accepted) 상태 코드가 반환됩니다.
            log.info("Lambda 알림 이벤트 전송 완료. 상태 코드: {}", response.toString());

        } catch (Exception e) {
            log.error("Lambda 알림 호출 중 에러 발생: {}", e.getMessage(), e);
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
    }
}
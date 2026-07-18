package com.jobai.backend.global.ai.exception;

import org.springframework.http.HttpStatusCode;

public class AiClientException extends RuntimeException {

    private final HttpStatusCode status;
    private final String responseBody;

    public AiClientException(HttpStatusCode status, String responseBody) {
        super("AI 호출 실패: status=" + status);
        this.status = status;
        this.responseBody = responseBody;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getResponseBody() {
        return responseBody;
    }
}

package com.jobai.backend.domain.ai.exception;

import org.springframework.http.HttpStatus;

public class AiClientException extends RuntimeException {

    private final HttpStatus status;
    private final String responseBody;

    public AiClientException(HttpStatus status, String responseBody) {
        super("AI 호출 실패: status=" + status);
        this.status = status;
        this.responseBody = responseBody;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getResponseBody() {
        return responseBody;
    }
}

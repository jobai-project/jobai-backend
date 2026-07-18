package com.jobai.backend.domain.summary.service;

/** LLM 요약 응답의 JSON 파싱 또는 검증 실패 시 발생하는 예외. */
public class JobSummaryParseException extends RuntimeException {

    public JobSummaryParseException(String message) {
        super(message);
    }

    public JobSummaryParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

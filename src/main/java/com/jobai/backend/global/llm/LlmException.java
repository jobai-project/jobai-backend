package com.jobai.backend.global.llm;

/** LLM 호출/파싱 중 발생한 오류. */
public class LlmException extends RuntimeException {
    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
    public LlmException(String message) {
        super(message);
    }
}

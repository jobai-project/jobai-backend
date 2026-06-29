package com.jobai.backend.domain.crawler.summary;

public class JobSummaryParseException extends RuntimeException {

    public JobSummaryParseException(String message) {
        super(message);
    }

    public JobSummaryParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.jobai.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버 POST /score/public 응답 DTO.
 */
public record ScorePublicResponse(
        double score,
        @JsonProperty("above_threshold") boolean aboveThreshold,
        double kw,
        double cs,
        double ws,
        double gp,
        @JsonProperty("matched_skills") List<String> matchedSkills,
        @JsonProperty("missing_skills") List<String> missingSkills,
        @JsonProperty("matched_certs") List<String> matchedCerts,
        @JsonProperty("missing_certs") List<String> missingCerts,
        @JsonProperty("job_cluster") String jobCluster,
        @JsonProperty("resume_cluster") String resumeCluster,
        @JsonProperty("score_reason") String scoreReason,
        List<String> penalties
) {
}

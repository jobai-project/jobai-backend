package com.jobai.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버 POST /score/private 응답 DTO.
 */
public record ScorePrivateResponse(
        double score,
        @JsonProperty("above_threshold") boolean aboveThreshold,
        @JsonProperty("matched_skills") List<String> matchedSkills,
        @JsonProperty("missing_skills") List<String> missingSkills,
        @JsonProperty("career_met") boolean careerMet,
        @JsonProperty("score_reason") String scoreReason,
        @JsonProperty("model_version") String modelVersion
) {
}

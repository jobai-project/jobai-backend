package com.jobai.backend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버 POST /score/private 요청 DTO.
 */
public record ScorePrivateRequest(
        @JsonProperty("jd_text") String jdText,
        @JsonProperty("jd_vec") List<Double> jdVec,
        @JsonProperty("resume_vec") List<Double> resumeVec,
        @JsonProperty("resume_skills") List<String> resumeSkills,
        @JsonProperty("experience_years") int experienceYears
) {
}

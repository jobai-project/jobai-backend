package com.jobai.backend.global.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 서버 POST /score/public 요청 DTO.
 */
public record ScorePublicRequest(
        @JsonProperty("job_id") Long jobId,
        String title,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("job_role") String jobRole,
        @JsonProperty("work_experience") String workExperience,
        @JsonProperty("recrut_type") String recrutType,
        @JsonProperty("apply_qualification") String applyQualification,
        @JsonProperty("application_method") String applicationMethod,
        @JsonProperty("html_content") String htmlContent,
        @JsonProperty("jd_text") String jdText,
        ResumePayload resume,
        @JsonProperty("jd_vec") List<Double> jdVec,
        @JsonProperty("resume_vec") List<Double> resumeVec
) {
    /** AI 서버가 기대하는 resume dict 스키마({@code skills/certs/experience_years/job_role/summary}). */
    public record ResumePayload(
            List<String> skills,
            List<String> certs,
            @JsonProperty("experience_years") int experienceYears,
            @JsonProperty("job_role") String jobRole,
            String summary
    ) {
    }
}

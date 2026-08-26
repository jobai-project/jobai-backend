package com.jobai.backend.global.kafka.event;

import java.util.List;

public record ScoringRequestEvent(
        String pipelineRunId,
        Long resumeId,
        Long memberId,
        String userEmail,
        Long postingId,
        String postingSource,
        String jdText,
        List<Double> jdVector,
        List<Double> resumeVector,
        List<String> resumeSkills,
        int experienceYears,
        int scoreThreshold,
        String postingTitle,
        String postingCompany,
        String postingLocation,
        String postingEmploymentType,
        String postingJobCategory,
        String postingDeadline
) {
}

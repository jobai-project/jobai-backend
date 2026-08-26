package com.jobai.backend.global.kafka.event;

import java.util.List;

public record ScoringResultEvent(
        String pipelineRunId,
        String userEmail,
        Long memberId,
        Long resumeId,
        Long postingId,
        String postingSource,
        int score,
        String scoreReason,
        List<String> matchedSkills,
        List<String> missingSkills,
        boolean careerMet,
        String modelVersion,
        int scoreThreshold,
        String postingTitle,
        String postingCompany,
        String postingLocation,
        String postingEmploymentType,
        String postingJobCategory,
        String postingDeadline
) {
}

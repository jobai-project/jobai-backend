package com.jobai.backend.global.kafka.event;

import java.time.Instant;

public record PipelineStageCompleteEvent(
        String pipelineRunId,
        String stage,
        int processedCount,
        String summary,
        Instant completedAt
) {
    public static final String COLLECTION = "COLLECTION";
    public static final String CLASSIFICATION = "CLASSIFICATION";
    public static final String EMBEDDING = "EMBEDDING";
}

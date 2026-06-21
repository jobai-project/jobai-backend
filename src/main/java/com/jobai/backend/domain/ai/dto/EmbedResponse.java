package com.jobai.backend.domain.ai.dto;

import java.util.List;

public record EmbedResponse(
        List<Double> vector
) {
}
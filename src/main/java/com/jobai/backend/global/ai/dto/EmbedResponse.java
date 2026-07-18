package com.jobai.backend.global.ai.dto;

import java.util.List;

public record EmbedResponse(
        List<Double> vector
) {
}
package com.jobai.backend.global.ai.dto;

import java.util.Objects;

public record EmbedRequest(
        String text
) {

    public EmbedRequest {
        Objects.requireNonNull(text, "text must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }
}
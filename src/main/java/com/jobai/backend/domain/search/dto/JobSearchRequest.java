package com.jobai.backend.domain.search.dto;

import jakarta.validation.constraints.NotBlank;

public record JobSearchRequest(
        @NotBlank String query,
        int page,
        int size
) {
    public JobSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
    }
}

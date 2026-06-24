package com.jobai.backend.domain.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record JobSearchRequest(
        @NotBlank String query,
        @Min(0) int page,
        @Min(1) @Max(100) int size
) {
}

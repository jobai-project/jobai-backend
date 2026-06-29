package com.jobai.backend.domain.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSummaryResponse {

    private Long jobPostingId;
    private String title;
    private String company;
    private String applyUrl;
    private SummaryDetail summary;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryDetail {
        private List<String> techStack;
        private List<String> responsibilities;
        private List<String> qualifications;
        private List<String> preferredQualifications;
    }
}

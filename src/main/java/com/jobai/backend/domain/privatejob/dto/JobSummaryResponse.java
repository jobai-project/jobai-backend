package com.jobai.backend.domain.privatejob.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 공고 요약 응답 DTO. 공고 기본 정보와 LLM이 생성한 구조화 요약을 포함한다.
 */
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

    /**
     * LLM이 생성한 구조화된 요약 정보.
     * 기술스택, 담당업무, 자격요건, 우대사항 4개 카테고리로 구성된다.
     */
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

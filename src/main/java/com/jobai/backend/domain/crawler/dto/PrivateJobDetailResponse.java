package com.jobai.backend.domain.crawler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사기업 공고 상세 조회 응답 DTO.
 * 공고 원문(description)과 함께 캐시된 요약이 있으면 summary도 포함한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateJobDetailResponse {

    private Long id;
    private String title;
    private String company;
    private String location;
    private String employmentType;
    private String jobCategory;
    private String description;
    private String applyUrl;
    private LocalDate deadline;
    private LocalDateTime createdAt;

    /** AI 매칭 점수 (비로그인/이력서 미업로드 시 null). */
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer matchScore;

    /** 매칭 점수 산출 근거 (비로그인/이력서 미업로드 시 null). */
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String scoreReason;

    /** 캐시된 요약이 있으면 포함, 없으면 null (JSON 응답에서 제외). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JobSummaryResponse.SummaryDetail summary;

    /**
     * PrivateJobPosting 엔티티로부터 상세 응답을 생성한다. 요약은 포함하지 않는다.
     *
     * @param posting 공고 엔티티
     * @return 상세 응답 DTO
     */
    public static PrivateJobDetailResponse from(PrivateJobPosting posting) {
        return PrivateJobDetailResponse.builder()
                .id(posting.getId())
                .title(posting.getTitle())
                .company(posting.getCompany())
                .location(posting.getLocation())
                .employmentType(posting.getEmploymentType())
                .jobCategory(posting.getJobCategory())
                .description(posting.getDescription())
                .applyUrl(posting.getApplyUrl())
                .deadline(posting.getDeadline())
                .createdAt(posting.getCreatedAt())
                .build();
    }

    /**
     * PrivateJobPosting 엔티티와 요약 정보로부터 상세 응답을 생성한다.
     *
     * @param posting 공고 엔티티
     * @param summary 구조화된 요약 정보
     * @return 요약이 포함된 상세 응답 DTO
     */
    public static PrivateJobDetailResponse from(PrivateJobPosting posting, JobSummaryResponse.SummaryDetail summary) {
        return PrivateJobDetailResponse.builder()
                .id(posting.getId())
                .title(posting.getTitle())
                .company(posting.getCompany())
                .location(posting.getLocation())
                .employmentType(posting.getEmploymentType())
                .jobCategory(posting.getJobCategory())
                .description(posting.getDescription())
                .applyUrl(posting.getApplyUrl())
                .deadline(posting.getDeadline())
                .createdAt(posting.getCreatedAt())
                .summary(summary)
                .build();
    }
}

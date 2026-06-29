package com.jobai.backend.domain.crawler.dto;

import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
}

package com.jobai.backend.domain.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class MemberRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateJobPreferenceDTO {
        private String careerType;         // 신입/경력
        private List<String> jobCategories; // 희망 직무 리스트
        private List<String> locations;     // 희망 지역 리스트
    }
}

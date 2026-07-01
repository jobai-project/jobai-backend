package com.jobai.backend.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateBasicInfoDTO {
        @NotNull(message = "채용 형태는 필수입니다.")
        private String careerType;      // 인턴/신입/경력직/계약직

        @NotNull(message = "희망 근무 지역은 필수입니다. 모두 삭제하려면 빈 배열을 보내주세요.")
        private List<String> locations; // 희망 근무 지역 리스트
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateJobCategoryDTO {
        @NotNull(message = "희망 직무는 필수입니다. 모두 삭제하려면 빈 배열을 보내주세요.")
        private List<String> jobCategories; // 희망 직무 리스트
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateNameDTO {

        @NotBlank(message = "이름은 공백일 수 없습니다.")
        @Size(max = 20, message = "이름은 최대 20자까지 가능합니다.")
        private String name;
    }
}

package com.jobai.backend.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        @Schema(description = "희망 채용 형태 목록. 허용값: 인턴, 신입, 경력직, 계약직",
                example = "[\"신입\", \"계약직\"]", nullable = true)
        @Size(max = 4, message = "채용 형태는 최대 4개까지 선택할 수 있습니다.")
        private List<@NotNull(message = "채용 형태 값은 null일 수 없습니다.") @Pattern(
                regexp = "^(인턴|신입|경력직|계약직)$",
                message = "채용 형태는 인턴, 신입, 경력직, 계약직 중 하나여야 합니다."
        ) String> careerType;

        @Schema(description = "희망 직무 카테고리 목록 (전체 교체됨, 기존 값을 지우려면 빈 배열 전달)",
                example = "[\"백엔드\", \"프론트엔드\"]", nullable = true)
        private List<String> jobCategories; // 희망 직무 리스트

        @Schema(description = "희망 근무 지역 목록 (전체 교체됨, 기존 값을 지우려면 빈 배열 전달)",
                example = "[\"서울\", \"경기\"]", nullable = true)
        private List<String> locations;     // 희망 지역 리스트
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateBasicInfoDTO {
        @NotNull(message = "채용 형태는 필수입니다.")
        @Size(min = 1, max = 4, message = "채용 형태는 1개 이상 4개 이하로 선택해야 합니다.")
        private List<@NotNull(message = "채용 형태 값은 null일 수 없습니다.") @Pattern(
                regexp = "^(인턴|신입|경력직|계약직)$",
                message = "채용 형태는 인턴, 신입, 경력직, 계약직 중 하나여야 합니다."
        ) String> careerType;

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

        @Schema(description = "변경할 이름 (필수, 공백 불가, 최대 20자)", example = "이정헌")
        @NotBlank(message = "이름은 공백일 수 없습니다.")
        @Size(max = 20, message = "이름은 최대 20자까지 가능합니다.")
        private String name;
    }
}

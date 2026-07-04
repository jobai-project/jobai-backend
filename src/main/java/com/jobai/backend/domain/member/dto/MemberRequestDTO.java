package com.jobai.backend.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
        @Schema(description = "경력 구분. 허용값: 신입, 경력", example = "신입", nullable = true)
        private String careerType;         // 신입/경력

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
    public static class UpdateNameDTO {

        @Schema(description = "변경할 이름 (필수, 공백 불가, 최대 20자)", example = "이정헌")
        @NotBlank(message = "이름은 공백일 수 없습니다.")
        @Size(max = 20, message = "이름은 최대 20자까지 가능합니다.")
        private String name;
    }
}

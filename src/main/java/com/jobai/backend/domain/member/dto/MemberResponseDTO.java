package com.jobai.backend.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class MemberResponseDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MyPageDTO {
        @Schema(description = "기본 프로필 정보")
        private ProfileInfo profile;

        @Schema(description = "희망 근무 조건")
        private JobPreferenceInfo jobPreference;

        @Schema(description = "등록된 이력서 목록 (없으면 빈 배열)")
        private List<ResumeInfo> resumes;
    }

    @Getter
    @Builder
    public static class ProfileInfo {
        @Schema(description = "이름", example = "이정헌")
        private String name;

        @Schema(description = "이메일 (구글 계정)", example = "user@gmail.com")
        private String email;

        @Schema(description = "프로필 이미지 URL (없으면 null)", example = "https://lh3.googleusercontent.com/...", nullable = true)
        private String profileImageUrl;
    }

    @Getter
    @Builder
    public static class JobPreferenceInfo {
        @Schema(description = "희망 채용 형태 목록. 값: 인턴, 신입, 경력직, 계약직",
                example = "[\"신입\", \"계약직\"]")
        private List<String> careerType;

        @Schema(description = "희망 직무 카테고리 목록", example = "[\"백엔드\", \"프론트엔드\"]")
        private List<String> jobCategories;

        @Schema(description = "희망 근무 지역 목록", example = "[\"서울\", \"경기\"]")
        private List<String> locations;
    }

    @Getter
    @Builder
    public static class ResumeInfo {
        @Schema(description = "이력서 고유 ID", example = "1")
        private Long resumeId;

        @Schema(description = "업로드 당시 원본 파일명", example = "이정헌_이력서.pdf")
        private String originalFilename;

        @Schema(description = "다운로드용 저장 파일 URL (S3 등)", example = "https://jobai-bucket.s3.ap-northeast-2.amazonaws.com/resumes/1.pdf")
        private String storedFileUrl; // 이력서 다운로드 경로

        @Schema(description = "마지막 수정일 (yyyy-MM-dd)", example = "2026-06-20")
        private LocalDate updatedAt;

        @Schema(description = "현재 대표 이력서 여부 (복수 업로드 중 매칭에 사용할 것 하나만 true)", example = "true")
        private boolean isActive;
    }
}

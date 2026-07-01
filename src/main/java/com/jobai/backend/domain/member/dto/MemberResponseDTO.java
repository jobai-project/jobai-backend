package com.jobai.backend.domain.member.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class MemberResponseDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MyPageDTO {
        private ProfileInfo profile;
        private JobPreferenceInfo jobPreference;
        private List<ResumeInfo> resumes;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HomeProfileDTO {
        private String name;
        private String jobCategory;
    }

    @Getter
    @Builder
    public static class ProfileInfo {
        private String name;
        private String email;
        private String profileImageUrl;
    }

    @Getter
    @Builder
    public static class JobPreferenceInfo {
        private String careerType;
        private List<String> jobCategories;
        private List<String> locations;
    }

    @Getter
    @Builder
    public static class ResumeInfo {
        private Long resumeId;
        private String originalFilename;
        private String storedFileUrl; // 이력서 다운로드 경로
        private LocalDate updatedAt;
        private boolean isActive;
    }
}

package com.jobai.backend.domain.publicInstitution.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "public_job_postings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@DiscriminatorValue("PUBLIC")
public class PublicJobPosting extends JobPosting {

    @Column(length = 20, unique = true)
    private String pblntfNo; // 채용 공고 고유 번호 (원본 공고 ID)

    @Column(length = 500)
    private String applyLink; // 사용자 지원 링크

    @Column(columnDefinition = "TEXT")
    private String applicationMethod; // 접수 방법

    @Column(length = 500)
    private String jobRole; // 모집 직무 (NCS 코드 명 리스트)

    @Column(columnDefinition = "TEXT")
    private String applyQualification; // 지원 자격

    @Column(columnDefinition = "TEXT")
    private String disqualificationReason; // 결격 사유

    @Column(columnDefinition = "TEXT")
    private String htmlContent; // 추출된 HTML/텍스트 본문

    private Boolean isClosed; // 마감 여부

    private LocalDate lastCollectedAt; // 마지막 수집 시각

    private LocalDate lastSyncedDate; // 상세 정보 동기화 시각

    public void updatePublicInfo(String pblntfNo, String applyLink, String applicationMethod, String jobRole,
                                 String applyQualification, String disqualificationReason, String htmlContent,
                                 Boolean isClosed, LocalDate lastCollectedAt, LocalDate lastSyncedDate,
                                 String title, String companyName, LocalDate beginDate, LocalDate endDate,
                                 String workExperience, String workRegion, String recrutType) {
        this.pblntfNo = pblntfNo;
        this.applyLink = applyLink;
        this.applicationMethod = applicationMethod;
        this.jobRole = jobRole;
        this.applyQualification = applyQualification;
        this.disqualificationReason = disqualificationReason;
        this.htmlContent = htmlContent;
        this.isClosed = isClosed;
        this.lastCollectedAt = lastCollectedAt;
        this.lastSyncedDate = lastSyncedDate;
        
        // 부모 필드 업데이트를 위한 메서드 호출
        super.updateBaseInfo(title, companyName, beginDate, endDate, workExperience, workRegion, recrutType);
    }
}

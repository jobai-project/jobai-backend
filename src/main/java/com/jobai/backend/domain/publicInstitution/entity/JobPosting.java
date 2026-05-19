package com.jobai.backend.domain.publicInstitution.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "job_postings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pblntfNo;      // 채용 공고 고유 번호

    private String pbancNm;       // 공고 제목
    private String instNm;        // 공공기관명
    private String recrutSeNm;    // 채용 구분 (예: 신입/경력)
    private String workRgnNm;     // 근무 지역
    private LocalDate pbancBgngDt; // 공고 시작일
    private LocalDate pbancEndDt;   // 공고 종료일

    // 고도화 및 AI 매칭을 위한 상세 필드
    @Column(columnDefinition = "TEXT")
    private String applicationMethod; // 접수 방법 (scrnprcdrMthdExpln 매핑)

    private String jobRole;           // 모집 직무 (ncsCdNmLst 매핑)

    @Column(columnDefinition = "TEXT")
    private String applyQualification; // 지원 자격

    @Column(columnDefinition = "TEXT")
    private String disqualificationReason; // 결격 사유

    private String jobDescriptionPdfUrl; // 직무기술서 PDF 링크

    // 중복 수집 방지를 위한 엔티티 업데이트 로직(상세 정보 전체를 안전하게 Upsert 하기 위한 통합 업데이트 로직)
    public void updateDetailedInfo(String pbancNm, String recrutSeNm, String workRgnNm, LocalDate pbancEndDt,
                                   String applicationMethod, String jobRole, String applyQualification,
                                   String disqualificationReason, String jobDescriptionPdfUrl) {
        this.pbancNm = pbancNm;
        this.recrutSeNm = recrutSeNm;
        this.workRgnNm = workRgnNm;
        this.pbancEndDt = pbancEndDt;
        this.applicationMethod = applicationMethod;
        this.jobRole = jobRole;
        this.applyQualification = applyQualification;
        this.disqualificationReason = disqualificationReason;
        this.jobDescriptionPdfUrl = jobDescriptionPdfUrl;
    }
}
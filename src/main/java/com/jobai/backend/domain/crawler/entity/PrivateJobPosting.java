package com.jobai.backend.domain.crawler.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "private_job_postings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_company_source_job_id",
                columnNames = {"company", "source_job_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PrivateJobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 식별 (upsert 기준) ---
    @Column(nullable = false)
    private String company;            // 회사 (kakao, coupang...)

    @Column(name = "source_job_id", nullable = false)
    private String sourceJobId;        // 회사가 준 원본 공고 ID

    // --- 공고 정보 ---
    @Column(length = 500)
    private String title;

    private String location;           // 근무지역
    private String employmentType;     // 고용형태
    private String jobCategory;        // 직무 분류

    @Column(columnDefinition = "TEXT")
    private String description;        // 공고 본문 (상세)

    @Column(length = 1000)
    private String applyUrl;           // 지원 링크

    private LocalDate deadline;        // 마감일

    // --- 운영 (업데이트·마감 처리) ---
    @Column(nullable = false)
    @Builder.Default
    private boolean isClosed = false;  // 마감 여부

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;  // 마지막 수집 시각

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // --- upsert 의 update 부분 -
    public void updateDetail(String title, String location, String employmentType,
                             String jobCategory, String description, String applyUrl,
                             LocalDate deadline, LocalDateTime now) {
        this.title = title;
        this.location = location;
        this.employmentType = employmentType;
        this.jobCategory = jobCategory;
        this.description = description;
        this.applyUrl = applyUrl;
        this.deadline = deadline;
        this.isClosed = false;          // 다시 수집됐으니 마감 아님
        this.lastSeenAt = now;          // 이번에 봤음
        this.updatedAt = now;
    }

    // 이번 수집에서 안 보임 → 마감 처리
    public void markClosed(LocalDateTime now) {
        this.isClosed = true;
        this.updatedAt = now;
    }
}
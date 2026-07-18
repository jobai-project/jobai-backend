package com.jobai.backend.domain.summary.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * LLM 요약 결과 캐시 엔티티.
 * 공고별 1건의 요약을 JSONB로 저장하고, 원본 변경 시 재생성한다.
 */
@Entity
@Table(name = "job_posting_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JobPostingSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_posting_id", nullable = false, unique = true)
    private Long jobPostingId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", nullable = false, columnDefinition = "jsonb")
    private String summaryJson;

    /** 최초 요약 생성 시각. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 요약 재생성 시각. 최초 생성 시에는 null. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "source_updated_at", nullable = false)
    private LocalDateTime sourceUpdatedAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 요약 내용과 소스 업데이트 시각을 갱신한다.
     *
     * @param summaryJson      새로 생성된 요약 JSON
     * @param sourceUpdatedAt  원본 공고의 최신 수정 시각
     */
    public void updateSummary(String summaryJson, LocalDateTime sourceUpdatedAt) {
        this.summaryJson = summaryJson;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.updatedAt = LocalDateTime.now();
    }
}

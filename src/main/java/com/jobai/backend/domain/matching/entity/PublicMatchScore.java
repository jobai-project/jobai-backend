package com.jobai.backend.domain.matching.entity;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 공기업 공고에 대한 이력서 매칭 점수 엔티티.
 * AI 서버의 /score/public(NCS) 응답 결과를 저장한다.
 */
@Entity
@Table(name = "public_match_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_resume_public_job",
                columnNames = {"resume_id", "public_job_posting_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PublicMatchScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resumes resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "public_job_posting_id", nullable = false)
    private PublicJobPosting publicJobPosting;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "score_reason", columnDefinition = "TEXT")
    private String scoreReason;

    @Column(name = "matched_skills", columnDefinition = "TEXT")
    private String matchedSkills;

    @Column(name = "missing_skills", columnDefinition = "TEXT")
    private String missingSkills;

    @Column(name = "matched_certs", columnDefinition = "TEXT")
    private String matchedCerts;

    @Column(name = "missing_certs", columnDefinition = "TEXT")
    private String missingCerts;

    @Column(name = "job_cluster", length = 50)
    private String jobCluster;

    @Column(name = "resume_cluster", length = 50)
    private String resumeCluster;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

package com.jobai.backend.domain.matching.entity;

import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사기업 공고에 대한 이력서 매칭 점수 엔티티.
 * AI 서버의 /score/private 응답 결과를 저장한다.
 */
@Entity
@Table(name = "private_match_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_resume_private_job",
                columnNames = {"resume_id", "private_job_posting_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PrivateMatchScore {

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
    @JoinColumn(name = "private_job_posting_id", nullable = false)
    private PrivateJobPosting privateJobPosting;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "score_reason", columnDefinition = "TEXT")
    private String scoreReason;

    @Column(name = "matched_skills", columnDefinition = "TEXT")
    private String matchedSkills;

    @Column(name = "missing_skills", columnDefinition = "TEXT")
    private String missingSkills;

    @Column(name = "career_met")
    private Boolean careerMet;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

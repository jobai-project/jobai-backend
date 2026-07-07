package com.jobai.backend.domain.scrap.entity;

import com.jobai.backend.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 공기업(JobPosting)과 사기업(PrivateJobPosting)은 완전히 별개의 엔티티/테이블이라
 * FK로는 둘 다 참조할 수 없다.
 * HomeJobCandidateRepository/JobEmbedding과 동일하게
 * source("PUBLIC"|"PRIVATE") + sourceId(원본 공고 PK) 조합으로 소스 무관하게 참조
 */
@Entity
@Table(
        name = "member_scrap_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_scrap_target",
                columnNames = {"member_id", "source", "source_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MemberScrapHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 10)
    private String source; // "PUBLIC" | "PRIVATE"

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @CreationTimestamp
    private LocalDateTime scrappedAt;
}

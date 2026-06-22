package com.jobai.backend.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "preferred_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PreferredJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id") // ERD의 외래키 명칭 매핑
    private Member member;

    @Column(name = "job_category", nullable = false, length = 100)
    private String jobCategory;

    public PreferredJob(Member member, String jobCategory) {
        this.member = member;
        this.jobCategory = jobCategory;
    }
}

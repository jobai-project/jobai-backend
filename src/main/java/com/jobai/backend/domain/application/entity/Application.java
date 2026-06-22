package com.jobai.backend.domain.application.entity;

import com.jobai.backend.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 50)
    private String companyName;

    @Column(length = 50)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private LocalDate appliedAt;
    private LocalDate interviewAt;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "create_at")
    private LocalDate createdAt;

    @Column(name = "updated_at")
    private LocalDate updatedAt;
}

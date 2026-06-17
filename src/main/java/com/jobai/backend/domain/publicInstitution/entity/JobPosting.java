package com.jobai.backend.domain.publicInstitution.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    // pgvector를 사용하기 위해 columnDefinition 설정 (PostgreSQL 환경 가정)
    @Column(columnDefinition = "vector(768)")
    private String embedding;

    private LocalDate beginDate; //공고 시작일
    private LocalDate endDate; // 공고 종료일

    @Column(length = 500)
    private String companyName; // 회사명

    @Column(length = 50)
    private String company_type; // 기업형태

    @Column(length = 500)
    private String title; // 공고 제목

    @Column(length = 50)
    private String recrutType; //고용형태 (경력/신입 등)

    @Column(length = 255)
    private String workRegion; //근무지역

}

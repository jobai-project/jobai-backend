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

    // 중복 수집 방지를 위한 엔티티 업데이트 로직
    public void updateInfo(String pbancNm, String recrutSeNm, String workRgnNm, LocalDate pbancEndDt) {
        this.pbancNm = pbancNm;
        this.recrutSeNm = recrutSeNm;
        this.workRgnNm = workRgnNm;
        this.pbancEndDt = pbancEndDt;
    }
}
package com.jobai.backend.domain.publicInstitution.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "job_postings")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "dtype")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @Column(name = "embedding", columnDefinition = "vector(768)")
//    @JdbcTypeCode(SqlTypes.FLOAT)
//    private float[] embedding;

    private LocalDate beginDate; 
    private LocalDate endDate; 

    @Column(length = 500)
    private String companyName; 

    @Column(length = 50)
    private String company_type; 

    @Column(length = 500)
    private String title; 

    @Column(length = 50)
    private String workExperience;

    @Column(length = 50)
    private String recrutType; //hireTypeNmLst



    @Column(length = 255)
    private String workRegion; 

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void updateBaseInfo(String title, String companyName, LocalDate beginDate, LocalDate endDate, String workExperience, String workRegion, String recrutType) {
        this.title = title;
        this.companyName = companyName;
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.workExperience = workExperience;
        this.workRegion = workRegion;
        this.recrutType = recrutType;
    }
}

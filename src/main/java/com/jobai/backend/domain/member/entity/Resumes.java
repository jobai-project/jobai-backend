package com.jobai.backend.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * 회원이 업로드한 이력서 엔티티.
 * <p>PDF 파일 메타데이터와 함께 파싱된 텍스트 및 기술스택 정보를 저장한다.</p>
 */
@Entity
@Table(name = "resumes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@DynamicInsert
public class Resumes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 255, name = "original_filename")
    private String originalFilename;

    @Column(length = 512, name = "stored_file_url")
    private String storedFileUrl;

    @Column(length = 255, name = "file_size")
    private String fileSize;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "updated_at")
    private LocalDate updatedAt;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "resume_skills", columnDefinition = "TEXT")
    private String resumeSkills;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(768)")
    private float[] embedding;

    /** 공기업(NCS) 매칭용 벡터. 사기업용 embedding과는 다른 임베딩 모델 공간이라 별도 컬럼으로 저장한다. */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "ncs_embedding", columnDefinition = "vector(768)")
    private float[] ncsEmbedding;

    /**
     * 이력서 파싱 결과를 업데이트한다.
     *
     * @param extractedText   PDF에서 추출한 원문 텍스트
     * @param resumeSkills    추출된 기술스택 JSON 배열 문자열 (예: {@code ["Java","Spring"]})
     * @param experienceYears 이력서에서 파싱된 경력 연수 (없으면 null)
     */
    public void updateParsedData(String extractedText, String resumeSkills, Integer experienceYears) {
        this.extractedText = extractedText;
        this.resumeSkills = resumeSkills;
        this.experienceYears = experienceYears;
    }

    /**
     * 이력서 임베딩 벡터를 업데이트한다.
     *
     * @param embedding 768차원 임베딩 벡터
     */
    public void updateEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    /**
     * 공기업(NCS) 매칭용 임베딩 벡터를 업데이트한다.
     *
     * @param ncsEmbedding 768차원 NCS 임베딩 벡터
     */
    public void updateNcsEmbedding(float[] ncsEmbedding) {
        this.ncsEmbedding = ncsEmbedding;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}

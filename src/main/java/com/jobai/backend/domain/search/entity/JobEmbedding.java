package com.jobai.backend.domain.search.entity;

import com.jobai.backend.global.model.JobSource;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 공고 임베딩 엔티티. 공고의 title + description/htmlContent를 768차원 벡터로 변환하여 저장한다.
 * 벡터 유사도 검색에 사용되며, source와 sourceId로 원본 공고를 논리적으로 참조한다.
 */
@Entity
@Table(name = "job_embeddings", uniqueConstraints =
    @UniqueConstraint(name = "uk_source_source_id", columnNames = {"source", "source_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobSource source;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(768)", nullable = false)
    private float[] embedding;

    @Column(name = "embedding_text", columnDefinition = "TEXT")
    private String embeddingText;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public JobEmbedding(JobSource source, Long sourceId, float[] embedding, String embeddingText) {
        this.source = source;
        this.sourceId = sourceId;
        this.embedding = embedding;
        this.embeddingText = embeddingText;
    }

    /** 임베딩 벡터와 원본 텍스트를 갱신한다. */
    public void updateEmbedding(float[] embedding, String embeddingText) {
        this.embedding = embedding;
        this.embeddingText = embeddingText;
    }
}

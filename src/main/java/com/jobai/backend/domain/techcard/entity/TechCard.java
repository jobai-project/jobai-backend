package com.jobai.backend.domain.techcard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tech_cards",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tech_card_source_external_id",
                columnNames = {"source", "external_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TechCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContentSource source;

    @Column(name = "external_id", nullable = false, length = 500)
    private String externalId;

    @Column(name = "original_title", nullable = false, length = 500)
    private String originalTitle;

    @Column(name = "original_url", nullable = false, length = 1000)
    private String originalUrl;

    @Column(nullable = false, length = 200)
    private String headline;

    @Column(nullable = false, length = 300)
    private String subtext;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}

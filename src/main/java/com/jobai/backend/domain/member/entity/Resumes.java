package com.jobai.backend.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "resumes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
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

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}

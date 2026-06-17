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

    @Column(length = 255)
    private String original_filename;

    @Column(length = 512)
    private String stored_file_url;

    @Column(length = 255)
    private String file_size;

    private Boolean is_active;

    private LocalDate updated_at;
}

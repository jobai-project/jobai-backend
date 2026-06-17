package com.jobai.backend.domain.member.entity;

import com.jobai.backend.global.apiPayload.code.BaseCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // 혹시 필요할까봐
    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 255)
    private String providerId;

    @Column(length = 20)
    private String provider;

    @Column(length = 255)
    private String career_type;

    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Member update(String name) {
        this.name = name;
        return this;
    }
}

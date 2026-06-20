package com.jobai.backend.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    private String provider; // "google" 저장

    private String providerId; // 구글의 유저 고유 ID (sub 값)

    // 사용자의 이름 등이 변경되었을 때를 위한 업데이트 메서드
    public Member update(String name) {
        this.name = name;
        return this;
    }
}
package com.jobai.backend.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "preferred_regions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PreferredRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 100)
    private String regionName;
}

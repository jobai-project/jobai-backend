package com.jobai.backend.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 255)
    private String providerId;

    @Column(length = 20)
    private String provider;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "member_career_types", joinColumns = @JoinColumn(name = "member_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "career_type", nullable = false, length = 20)
    private List<String> careerTypes = new ArrayList<>();

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean onboardingCompleted = false;

    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // --- 연관 관계 추가 (Cascade 및 고판정 제거 설정) ---
    @Builder.Default
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreferredJob> prefJobs = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreferredRegion> prefLocations = new ArrayList<>();

    @Builder.Default // 빌더로 객체를 만들 때도 이 필드는 기본적으로 빈 리스트로 초기화하라는 어노테이션
    // mappedBy를 통해 연관관계의 주인을 지정.
    // cascade를 통해 Member 엔티티의 영속상 상태를 전파시킴.
    // orphanRemoval을 통해 부모 객체와 관계가 끊어진 엔티티를 자동으로 삭제.
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Resumes> resumes = new ArrayList<>();

    // --- 비즈니스 로직 (통 업데이트 편의 메서드) ---
    public void updateJobPreferences(List<String> careerTypes, List<String> jobCategories, List<String> locations) {
        updateBasicInfo(careerTypes, locations);
        updateJobCategories(jobCategories);
    }

    // 온보딩 1단계: 희망 근무 지역 + 희망 채용 형태 (다른 필드는 건드리지 않음)
    public void updateBasicInfo(List<String> careerTypes, List<String> locations) {
        this.careerTypes.clear();
        if (careerTypes != null) {
            this.careerTypes.addAll(careerTypes.stream().distinct().toList());
        }

        // 기존 지역 리스트 비우고 새 리스트로 교체 (JPA orphanRemoval 작동)
        this.prefLocations.clear();
        if (locations != null) {
            locations.forEach(loc -> this.prefLocations.add(new PreferredRegion(this, loc)));
        }
    }

    // 온보딩 2단계: 희망 직무 (다른 필드는 건드리지 않음)
    public void updateJobCategories(List<String> jobCategories) {
        // 기존 직무 리스트 비우고 새 리스트로 교체 (JPA orphanRemoval 작동)
        this.prefJobs.clear();
        if (jobCategories != null) {
            jobCategories.forEach(category -> this.prefJobs.add(new PreferredJob(this, category)));
        }
    }

    public Member update(String name) {
        this.name = name;
        return this;
    }

    // 온보딩 4단계(알림설정) 저장 완료 시 호출: 온보딩 전체 완료 처리
    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }
}

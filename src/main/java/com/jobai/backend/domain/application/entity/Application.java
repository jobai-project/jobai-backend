package com.jobai.backend.domain.application.entity;

import com.jobai.backend.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;

@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 50)
    private String companyName;

    @Column(length = 50)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private LocalDate appliedAt;
    private LocalDate interviewAt;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "create_at")
    @CreationTimestamp
    private LocalDate createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDate updatedAt;

    // 부분 수정을 지원하는 비즈니스 메서드 추가 TODO: 프론트 및 기획이랑 기능 협의 필요
    // 해당 필드에 값이 들어왔을 때만 수정함.
    public void update(String companyName, String jobTitle, ApplicationStatus status,
                       LocalDate appliedAt, LocalDate interviewAt, String memo) {
        if (companyName != null) this.companyName = companyName;
        if (jobTitle != null) this.jobTitle = jobTitle;
        if (status != null) this.status = status;
        if (appliedAt != null)this.appliedAt = appliedAt;
        if (interviewAt != null)this.interviewAt = interviewAt;
        if (memo != null)this.memo = memo;
    }
}

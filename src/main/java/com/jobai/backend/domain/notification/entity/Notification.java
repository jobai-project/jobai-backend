package com.jobai.backend.domain.notification.entity;

import com.jobai.backend.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private Boolean emailNotification;
    private Boolean slackNotification;
    private Boolean discordNotification;

    @Column(length = 500)
    private String slackWebhookUrl;

    @Column(length = 500)
    private String discordWebhookUrl;

    // 홈 화면 노출 기준: 이 점수 이상인 매칭 공고만 추천 알림/노출 대상
    @Column(nullable = false)
    private Integer matchScoreThreshold;

    public static Notification createDefault(Member member) {
        return Notification.builder()
                .member(member)
                .emailNotification(true) // 이메일은 이미 인증된 채널이므로 기본 활성화
                .slackNotification(false)
                .discordNotification(false)
                .matchScoreThreshold(70)
                .build();
    }

    // 온보딩 4단계: 채널 on/off + 적합도 기준 저장
    public void updateSettings(Boolean emailNotification, Boolean slackNotification, Boolean discordNotification, Integer matchScoreThreshold) {
        this.emailNotification = emailNotification;
        this.slackNotification = slackNotification;
        this.discordNotification = discordNotification;
        this.matchScoreThreshold = matchScoreThreshold;
    }
}

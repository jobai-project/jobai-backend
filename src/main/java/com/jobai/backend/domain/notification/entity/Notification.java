package com.jobai.backend.domain.notification.entity;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
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
    private String slack_webhook_url;

    @Column(length = 500)
    private String discord_webhook_url;

}

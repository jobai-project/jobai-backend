package com.jobai.backend.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "notification_match_batch_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationMatchBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private NotificationMatchBatch batch;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 255)
    private String companyName;

    @Column(length = 255)
    private String location;

    @Column(length = 255)
    private String employmentType;

    private LocalDate deadline;

    @Column(nullable = false)
    private Integer matchScore;

    @Column(nullable = false, length = 500)
    private String detailLinkUrl;

    void assignBatch(NotificationMatchBatch batch) {
        this.batch = batch;
    }
}

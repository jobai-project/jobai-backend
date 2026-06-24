package com.jobai.backend.domain.crawler.entity;

import com.jobai.backend.domain.crawler.classify.JobCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "private_job_postings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_company_source_job_id",
                columnNames = {"company", "source_job_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PrivateJobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- 식별 (upsert 기준) ---
    @Column(nullable = false)
    private String company;            // 회사 (kakao, coupang...)

    @Column(name = "source_job_id", nullable = false)
    private String sourceJobId;        // 회사가 준 원본 공고 ID

    // --- 공고 정보 ---
    @Column(length = 500)
    private String title;

    private String location;           // 근무지역
    private String employmentType;     // 고용형태
    private String jobCategory;        // 직무 분류

    @Column(columnDefinition = "TEXT")
    private String description;        // 공고 본문 (상세)

    @Column(length = 1000)
    private String applyUrl;           // 지원 링크

    private LocalDate deadline;        // 마감일

    // --- 운영 (업데이트·마감 처리) ---
    @Column(nullable = false)
    @Builder.Default
    private boolean isClosed = false;  // 마감 여부

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;  // 마지막 수집 시각

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // --- upsert 의 update 부분 -
    public void updateDetail(String title, String location, String employmentType,
                             String description, String applyUrl,
                             LocalDate deadline, LocalDateTime now) {
        this.title = title;
        this.location = location;
        this.employmentType = employmentType;
        this.description = description;
        this.applyUrl = applyUrl;
        this.deadline = deadline;
        this.isClosed = false;          // 다시 수집됐으니 마감 아님
        this.lastSeenAt = now;          // 이번에 봤음
        this.updatedAt = now;
    }

    // 이번 수집에서 안 보임 → 마감 처리
    public void markClosed(LocalDateTime now) {
        this.isClosed = true;
        this.updatedAt = now;
    }

    /**
     * 새로 수집한 내용이 현재 저장된 내용과 같은지 판별한다(변경 감지).
     *
     * <p>upsert 시 내용이 안 바뀐 공고를 매번 UPDATE 하지 않기 위해 쓴다.
     * 비교 대상은 updateDetail 이 갱신하는 공고 정보 필드 전부:
     * title, location, employmentType, jobCategory, description, applyUrl, deadline.
     *
     * <p>주의: 마감(isClosed=true)됐던 공고가 다시 보이면 내용이 같아도 되살려야(update) 하므로,
     * 이 메서드는 "내용 동일" 만 판단하고, 마감 부활 여부는 호출하는 쪽에서 isClosed 로 함께 본다.
     * deadline 은 null 일 수 있어 Objects.equals 로 null 안전 비교한다.
     */
    public boolean hasSameContent(String title, String location, String employmentType,
                                  String description, String applyUrl,
                                  LocalDate deadline) {
        return Objects.equals(this.title, title)
                && Objects.equals(this.location, location)
                && Objects.equals(this.employmentType, employmentType)
                && Objects.equals(this.description, description)
                && Objects.equals(this.applyUrl, applyUrl)
                && Objects.equals(this.deadline, deadline);
    }

    /**
     * 내용 변화가 없을 때, 이번 수집에서 봤다는 표시(lastSeenAt)만 갱신한다.
     * updatedAt 은 건드리지 않는다(실제 변경이 아니므로). 변경 감지로 인한 불필요한 UPDATE 를 막는다.
     *
     * <p>단, lastSeenAt 갱신 자체가 dirty checking 으로 UPDATE 를 유발할 수 있다.
     * 그래도 updateDetail 전체보다 의미가 분명하고, "마지막으로 살아있던 시각" 기록은 마감 판정에 유용하다.
     */
    public void touch(LocalDateTime now) {
        this.lastSeenAt = now;
    }

    /**
     * LLM 직무 분류 결과를 반영한다. jobCategory 만 갱신한다.
     * 분류는 수집 내용 변경이 아니므로 updatedAt 은 건드리지 않는다
     * (updatedAt 은 "수집 내용이 바뀐 시각"으로만 의미를 둔다).
     */
    public void classifyAs(JobCategory category) {
        this.jobCategory = Objects.requireNonNull(category, "category").getLabel();
    }
}
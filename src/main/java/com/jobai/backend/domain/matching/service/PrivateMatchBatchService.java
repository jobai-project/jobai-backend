package com.jobai.backend.domain.matching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.ai.client.AiScoringClient;
import com.jobai.backend.global.ai.dto.ScorePrivateRequest;
import com.jobai.backend.global.ai.dto.ScorePrivateResponse;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.global.enums.JobSource;
import com.jobai.backend.global.enums.JobCategory;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import com.jobai.backend.domain.search.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

/**
 * 새벽 배치용 점수 산출 서비스.
 * 신규 공고(점수 미산출)와 변경된 공고(posting.updatedAt > score.createdAt)에 대해서만
 * AI 점수를 산출한다.
 *
 * <p>이력서 업로드 시의 전체 재산출({@link PrivateMatchingService})과 달리,
 * 기존 점수를 유지하고 신규/변경분만 증분 처리한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateMatchBatchService {

    /** self-injection: 프록시를 경유하여 @Transactional이 정상 동작하도록 한다. */
    @Lazy
    @Autowired
    private PrivateMatchBatchService self;

    private final AiScoringClient aiScoringClient;
    private final PrivateJobPostingRepository privateJobPostingRepository;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final ResumesRepository resumesRepository;
    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final BatchNotificationHelper batchNotificationHelper;

    /**
     * 모든 활성 이력서 × 활성 공고 조합에서 점수가 없거나 변경된 공고에 대해 점수를 산출한다.
     *
     * <p>처리 대상:</p>
     * <ul>
     *   <li><b>신규 공고:</b> 해당 이력서에 대해 아직 {@link PrivateMatchScore}가 없는 공고</li>
     *   <li><b>변경 공고:</b> 기존 점수는 있지만 {@code posting.updatedAt > score.createdAt}인 공고</li>
     * </ul>
     *
     * <p>이력서별 try-catch로 한 이력서 처리 실패 시에도 나머지는 계속 진행한다.</p>
     */
    /** @return 점수 산출 요약 및 사용자별 알림 대상 공고 데이터 */
    public BatchNotificationHelper.BatchScoringResult scoreNewAndUpdatedPostings() {
        List<Resumes> activeResumes = resumesRepository.findAllActiveWithEmbedding();
        if (activeResumes.isEmpty()) {
            log.info("[배치점수] 활성 이력서가 없어 점수 산출 건너뜀");
            return new BatchNotificationHelper.BatchScoringResult("활성 이력서 0건 — 건너뜀", Map.of());
        }

        List<PrivateJobPosting> activePostings =
                privateJobPostingRepository.findActiveByValidCategories(JobCategory.matchTargetLabels());
        if (activePostings.isEmpty()) {
            log.info("[배치점수] 활성 공고가 없어 점수 산출 건너뜀");
            return new BatchNotificationHelper.BatchScoringResult(
                    "이력서 " + activeResumes.size() + "건, 활성 공고 0건 — 건너뜀", Map.of());
        }

        Map<Long, PrivateJobPosting> postingMap = activePostings.stream()
                .collect(Collectors.toMap(PrivateJobPosting::getId, p -> p));

        log.info("[배치점수] 시작 — 이력서 {}개, 활성 공고 {}개", activeResumes.size(), activePostings.size());

        int totalNew = 0;
        int totalUpdated = 0;
        int totalFail = 0;
        int resumeError = 0;
        String lastError = "";
        // 알림 발송은 호출자(스케줄러)에서 사기업·공기업 결과를 합산 후 1회 처리한다.
        Map<String, BatchNotificationHelper.MemberNotifications> notifications = new LinkedHashMap<>();

        for (Resumes resume : activeResumes) {
            try {
                ScoreResult result = self.scoreForResume(resume, postingMap);
                totalNew += result.newCount();
                totalUpdated += result.updatedCount();
                totalFail += result.failCount();

                if (!result.aboveThresholdPostings().isEmpty()) {
                    notifications.put(resume.getMember().getEmail(),
                            new BatchNotificationHelper.MemberNotifications(
                                    resume.getMember(), result.aboveThresholdPostings()));
                }
            } catch (Exception e) {
                resumeError++;
                lastError = e.getMessage();
                log.error("[배치점수] 이력서 {} 처리 중 오류: {}", resume.getId(), e.getMessage(), e);
            }
        }

        log.info("[배치점수] 완료 — 신규 점수 {}건, 변경 재산출 {}건, 실패 {}건",
                totalNew, totalUpdated, totalFail);
        String summary = String.format("이력서 %d건 × 공고 %d건 | 신규 %d, 변경 %d, 실패 %d",
                activeResumes.size(), activePostings.size(), totalNew, totalUpdated, totalFail);
        if (resumeError > 0) {
            summary += " | 이력서오류 " + resumeError + "건: " + lastError;
        }
        return new BatchNotificationHelper.BatchScoringResult(summary, notifications);
    }

    /** 이력서별 점수 산출 결과. 알림 대상 공고 목록을 포함한다. */
    record ScoreResult(int newCount, int updatedCount, int failCount,
                       List<BatchNotificationHelper.ScoredPosting> aboveThresholdPostings) {
    }

    /**
     * 한 이력서에 대해 신규/변경 공고의 점수를 산출한다.
     *
     * <p>기존 점수를 공고ID별로 맵핑한 뒤, 활성 공고를 순회하며
     * 점수가 없으면 신규 산출, 공고 변경 시각이 점수 생성 시각보다 늦으면 재산출한다.
     * 산출된 점수가 사용자의 {@code matchScoreThreshold} 이상이면 알림 대상으로 수집한다.</p>
     *
     * @param resume           점수를 산출할 이력서
     * @param activePostingMap 활성 공고 맵 (공고 ID → 엔티티)
     * @return 산출 건수 및 임계값 이상 공고 목록을 담은 {@link ScoreResult}
     */
    @Transactional
    public ScoreResult scoreForResume(Resumes resume, Map<Long, PrivateJobPosting> activePostingMap) {
        Long resumeId = resume.getId();
        String email = resume.getMember().getEmail();

        // 사용자 임계값 조회 (조회 실패 시에도 점수 산출은 계속 진행)
        int threshold = 70;
        try {
            threshold = notificationRepository.findByMemberEmail(email)
                    .map(Notification::getMatchScoreThreshold)
                    .orElse(70);
        } catch (Exception e) {
            log.warn("[배치점수] 알림 임계값 조회 실패, 기본값 사용: email={}, error={}", email, e.getMessage());
        }

        // 기존 점수 조회 → 공고ID별 점수 맵
        List<PrivateMatchScore> existingScores = privateMatchScoreRepository.findByResumeId(resumeId);
        Map<Long, PrivateMatchScore> scoreByPostingId = existingScores.stream()
                .collect(Collectors.toMap(
                        s -> s.getPrivateJobPosting().getId(),
                        s -> s
                ));

        List<String> resumeSkills = parseSkills(resume.getResumeSkills());
        List<Double> resumeVec = toDoubleList(resume.getEmbedding());
        int experienceYears = resolveExperienceYears(resume);

        int newCount = 0;
        int updatedCount = 0;
        int failCount = 0;
        List<BatchNotificationHelper.ScoredPosting> aboveThreshold = new ArrayList<>();
        // AI 통신 블로킹 대기로 루프 소요 시간이 길어질 수 있으므로,
        // 기준 시간을 루프 진입 전에 한 번만 계산하여 공고별 평가 시점 차이로 인한 누락을 방지한다.
        LocalDateTime recentThreshold = LocalDateTime.now().minusHours(24);

        for (PrivateJobPosting posting : activePostingMap.values()) {
            PrivateMatchScore existing = scoreByPostingId.get(posting.getId());

            if (existing == null) {
                // 신규: 점수 없음 → 산출
                try {
                    int score = calculateAndSave(resume, posting, resumeVec, resumeSkills, experienceYears);
                    newCount++;
                    // 공고 자체가 최근 24시간 내에 수집된 경우에만 알림 대상에 포함한다.
                    // 새 사용자의 첫 배치 실행 시 기존 공고 전체에 대한 알림 발송을 방지한다.
                    boolean isRecentlyCollected = posting.getCreatedAt() != null
                            && posting.getCreatedAt().isAfter(recentThreshold);
                    if (score >= threshold && isRecentlyCollected) {
                        aboveThreshold.add(new BatchNotificationHelper.ScoredPosting(
                                "PRIVATE",
                                posting.getTitle(),
                                posting.getCompany(),
                                score,
                                posting.getId(),
                                "/jobs/private/",
                                posting.getLocation(),
                                posting.getEmploymentType(),
                                posting.getDeadline()));
                    }
                } catch (Exception e) {
                    log.warn("[배치점수] 신규 점수 산출 실패: resumeId={}, postingId={}, error={}",
                            resumeId, posting.getId(), e.getMessage());
                    failCount++;
                }
            } else if (posting.getUpdatedAt() != null
                    && existing.getCreatedAt() != null
                    && posting.getUpdatedAt().isAfter(existing.getCreatedAt())) {
                // 변경: 공고가 점수 산출 이후 업데이트됨 → 기존 삭제 후 재산출
                // try-catch 없음: 실패 시 예외가 트랜잭션 경계까지 전파되어 delete도 함께 롤백된다.
                privateMatchScoreRepository.delete(existing);
                privateMatchScoreRepository.flush();
                calculateAndSave(resume, posting, resumeVec, resumeSkills, experienceYears);
                updatedCount++;
                // 변경 공고는 알림 제외 — 내용 변경 감지가 CSS 순서 등 비의미적 차이로 오탐될 수 있어 신규 공고만 알림 발송
            }
            // else: 기존 점수 존재 + 변경 없음 → skip
        }

        if (newCount + updatedCount > 0) {
            log.info("[배치점수] resumeId={} — 신규 {}, 변경 {}, 실패 {}, 알림대상 {}",
                    resumeId, newCount, updatedCount, failCount, aboveThreshold.size());
        }

        return new ScoreResult(newCount, updatedCount, failCount, aboveThreshold);
    }

    /**
     * AI 서버를 호출하여 매칭 점수를 산출하고 {@link PrivateMatchScore}로 저장한다.
     *
     * @param resume          이력서 엔티티
     * @param posting         공고 엔티티
     * @param resumeVec       이력서 임베딩 벡터 (768차원)
     * @param resumeSkills    이력서에서 추출된 스킬 목록
     * @param experienceYears 경력 연수
     * @throws IllegalStateException 공고 임베딩 생성 불가 또는 AI 응답이 null인 경우
     */
    private int calculateAndSave(Resumes resume, PrivateJobPosting posting,
                                 List<Double> resumeVec, List<String> resumeSkills,
                                 int experienceYears) {
        float[] jdVec = getOrCreateJobEmbedding(posting);
        if (jdVec == null) {
            throw new IllegalStateException("공고 임베딩 생성 불가: postingId=" + posting.getId());
        }

        String jdText = buildJdText(posting);
        ScorePrivateRequest request = new ScorePrivateRequest(
                jdText, toDoubleList(jdVec), resumeVec, resumeSkills, experienceYears);

        ScorePrivateResponse response = aiScoringClient.scorePrivate(request).block();
        if (response == null) {
            throw new IllegalStateException("AI 점수 응답 null: postingId=" + posting.getId());
        }

        int score = (int) Math.round(response.score());
        privateMatchScoreRepository.save(PrivateMatchScore.builder()
                .member(resume.getMember())
                .resume(resume)
                .privateJobPosting(posting)
                .score(score)
                .scoreReason(response.scoreReason())
                .matchedSkills(toJson(response.matchedSkills()))
                .missingSkills(toJson(response.missingSkills()))
                .careerMet(response.careerMet())
                .modelVersion(response.modelVersion())
                .build());
        return score;
    }


    /**
     * 공고의 임베딩 벡터를 조회하거나, 없으면 생성한다.
     *
     * @param posting 대상 공고
     * @return 임베딩 벡터. 생성 실패 시 {@code null}
     */
    private float[] getOrCreateJobEmbedding(PrivateJobPosting posting) {
        Optional<JobEmbedding> existing = jobEmbeddingRepository
                .findBySourceAndSourceId(JobSource.PRIVATE, posting.getId());
        if (existing.isPresent()) {
            return existing.get().getEmbedding();
        }

        try {
            embeddingService.embedPrivatePosting(posting);
            return jobEmbeddingRepository
                    .findBySourceAndSourceId(JobSource.PRIVATE, posting.getId())
                    .map(JobEmbedding::getEmbedding)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("공고 임베딩 생성 실패: postingId={}, error={}", posting.getId(), e.getMessage());
            return null;
        }
    }

    /** 공고의 제목과 설명을 하나의 텍스트로 결합한다. AI 점수 산출 요청에 사용된다. */
    private String buildJdText(PrivateJobPosting posting) {
        String title = posting.getTitle() != null ? posting.getTitle() : "";
        String desc = posting.getDescription() != null ? posting.getDescription() : "";
        return title + "\n" + desc;
    }

    private int resolveExperienceYears(Resumes resume) {
        return resume.getExperienceYears() != null ? resume.getExperienceYears() : 0;
    }

    private List<String> parseSkills(String skillsJson) {
        if (skillsJson == null || skillsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(skillsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<Double> toDoubleList(float[] floats) {
        List<Double> list = new ArrayList<>(floats.length);
        for (float f : floats) {
            list.add((double) f);
        }
        return list;
    }

    private String toJson(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}

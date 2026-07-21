package com.jobai.backend.domain.matching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.ai.client.AiScoringClient;
import com.jobai.backend.global.ai.dto.ScorePublicRequest;
import com.jobai.backend.global.ai.dto.ScorePublicResponse;
import com.jobai.backend.domain.matching.entity.PublicMatchScore;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.global.enums.JobSource;
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

/**
 * 새벽 배치용 공기업 점수 산출 서비스.
 * 신규 공고(점수 미산출)와 변경된 공고(posting.updatedAt > score.createdAt)에 대해서만
 * AI 점수를 산출한다.
 *
 * <p>이력서 업로드 시의 전체 재산출({@link PublicMatchingService})과 달리,
 * 기존 점수를 유지하고 신규/변경분만 증분 처리한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicMatchBatchService {

    private static final int MAX_SUMMARY_LENGTH = 4000;

    /** self-injection: 프록시를 경유하여 @Transactional이 정상 동작하도록 한다. */
    @Lazy
    @Autowired
    private PublicMatchBatchService self;

    private final AiScoringClient aiScoringClient;
    private final JobPostingRepository jobPostingRepository;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final PublicMatchScoreRepository publicMatchScoreRepository;
    private final ResumesRepository resumesRepository;
    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final BatchNotificationHelper batchNotificationHelper;

    /**
     * 모든 활성 이력서 × 활성 공고 조합에서 점수가 없거나 변경된 공고에 대해 점수를 산출한다.
     *
     * <p>처리 대상:</p>
     * <ul>
     *   <li><b>신규 공고:</b> 해당 이력서에 대해 아직 {@link PublicMatchScore}가 없는 공고</li>
     *   <li><b>변경 공고:</b> 기존 점수는 있지만 {@code posting.updatedAt > score.createdAt}인 공고</li>
     * </ul>
     *
     * <p>이력서별 try-catch로 한 이력서 처리 실패 시에도 나머지는 계속 진행한다.</p>
     */
    /** @return 결과 요약 문자열 */
    public String scoreNewAndUpdatedPostings() {
        List<Resumes> activeResumes = resumesRepository.findAllActiveWithNcsEmbedding();
        if (activeResumes.isEmpty()) {
            log.info("[공기업 배치점수] 활성 이력서가 없어 점수 산출 건너뜀");
            return "활성 이력서(NCS) 0건 — 건너뜀";
        }

        List<PublicJobPosting> activePostings = jobPostingRepository.findActivePublicPostings();
        if (activePostings.isEmpty()) {
            log.info("[공기업 배치점수] 활성 공고가 없어 점수 산출 건너뜀");
            return "이력서 " + activeResumes.size() + "건, 활성 공고 0건 — 건너뜀";
        }

        Map<Long, PublicJobPosting> postingMap = activePostings.stream()
                .collect(Collectors.toMap(PublicJobPosting::getId, p -> p));

        log.info("[공기업 배치점수] 시작 — 이력서 {}개, 활성 공고 {}개", activeResumes.size(), activePostings.size());

        int totalNew = 0;
        int totalUpdated = 0;
        int totalFail = 0;

        for (Resumes resume : activeResumes) {
            try {
                ScoreResult result = self.scoreForResume(resume, postingMap);
                totalNew += result.newCount();
                totalUpdated += result.updatedCount();
                totalFail += result.failCount();

                batchNotificationHelper.sendIfNeeded(
                        resume.getMember(), result.aboveThresholdPostings(), "새 추천 공고 (공기업)");
            } catch (Exception e) {
                log.error("[공기업 배치점수] 이력서 {} 처리 중 오류: {}", resume.getId(), e.getMessage(), e);
            }
        }

        log.info("[공기업 배치점수] 완료 — 신규 점수 {}건, 변경 재산출 {}건, 실패 {}건",
                totalNew, totalUpdated, totalFail);
        return String.format("이력서 %d건 × 공고 %d건 | 신규 %d, 변경 %d, 실패 %d",
                activeResumes.size(), activePostings.size(), totalNew, totalUpdated, totalFail);
    }

    /** 이력서별 점수 산출 결과. 알림 대상 공고 목록을 포함한다. */
    record ScoreResult(int newCount, int updatedCount, int failCount,
                       List<BatchNotificationHelper.ScoredPosting> aboveThresholdPostings) {
    }

    /**
     * 한 이력서에 대해 신규/변경 공고의 점수를 산출한다.
     *
     * <p>산출된 점수가 사용자의 {@code matchScoreThreshold} 이상이면 알림 대상으로 수집한다.</p>
     *
     * @param resume           점수를 산출할 이력서
     * @param activePostingMap 활성 공고 맵 (공고 ID → 엔티티)
     * @return 산출 건수 및 임계값 이상 공고 목록을 담은 {@link ScoreResult}
     */
    @Transactional
    public ScoreResult scoreForResume(Resumes resume, Map<Long, PublicJobPosting> activePostingMap) {
        Long resumeId = resume.getId();
        String email = resume.getMember().getEmail();

        // 사용자 임계값 조회 (조회 실패 시에도 점수 산출은 계속 진행)
        int threshold = 70;
        try {
            threshold = notificationRepository.findByMemberEmail(email)
                    .map(Notification::getMatchScoreThreshold)
                    .orElse(70);
        } catch (Exception e) {
            log.warn("[공기업 배치점수] 알림 임계값 조회 실패, 기본값 사용: email={}, error={}", email, e.getMessage());
        }

        List<PublicMatchScore> existingScores = publicMatchScoreRepository.findByResumeId(resumeId);
        Map<Long, PublicMatchScore> scoreByPostingId = existingScores.stream()
                .collect(Collectors.toMap(
                        s -> s.getPublicJobPosting().getId(),
                        s -> s
                ));

        ScorePublicRequest.ResumePayload resumePayload = buildResumePayload(resume);
        List<Double> resumeVec = toDoubleList(resume.getNcsEmbedding());

        int newCount = 0;
        int updatedCount = 0;
        int failCount = 0;
        List<BatchNotificationHelper.ScoredPosting> aboveThreshold = new ArrayList<>();
        // AI 통신 블로킹 대기로 루프 소요 시간이 길어질 수 있으므로,
        // 기준 시간을 루프 진입 전에 한 번만 계산하여 공고별 평가 시점 차이로 인한 누락을 방지한다.
        LocalDateTime recentThreshold = LocalDateTime.now().minusHours(24);

        for (PublicJobPosting posting : activePostingMap.values()) {
            PublicMatchScore existing = scoreByPostingId.get(posting.getId());

            if (existing == null) {
                try {
                    int score = calculateAndSave(resume, posting, resumePayload, resumeVec);
                    newCount++;
                    // 공고 자체가 최근 24시간 내에 수집된 경우에만 알림 대상에 포함한다.
                    // 새 사용자의 첫 배치 실행 시 기존 공고 전체에 대한 알림 발송을 방지한다.
                    boolean isRecentlyCollected = posting.getCreatedAt() != null
                            && posting.getCreatedAt().isAfter(recentThreshold);
                    if (score >= threshold && isRecentlyCollected) {
                        aboveThreshold.add(new BatchNotificationHelper.ScoredPosting(
                                posting.getTitle(), posting.getCompanyName(), score, posting.getId(), "/jobs/public/"));
                    }
                } catch (Exception e) {
                    log.warn("[공기업 배치점수] 신규 점수 산출 실패: resumeId={}, postingId={}, error={}",
                            resumeId, posting.getId(), e.getMessage());
                    failCount++;
                }
            } else if (posting.getUpdatedAt() != null
                    && existing.getCreatedAt() != null
                    && posting.getUpdatedAt().isAfter(existing.getCreatedAt())) {
                publicMatchScoreRepository.delete(existing);
                publicMatchScoreRepository.flush();
                int score = calculateAndSave(resume, posting, resumePayload, resumeVec);
                updatedCount++;
                if (score >= threshold) {
                    aboveThreshold.add(new BatchNotificationHelper.ScoredPosting(
                            posting.getTitle(), posting.getCompanyName(), score, posting.getId(), "/jobs/public/"));
                }
            }
            // else: 기존 점수 존재 + 변경 없음 → skip
        }

        if (newCount + updatedCount > 0) {
            log.info("[공기업 배치점수] resumeId={} — 신규 {}, 변경 {}, 실패 {}, 알림대상 {}",
                    resumeId, newCount, updatedCount, failCount, aboveThreshold.size());
        }

        return new ScoreResult(newCount, updatedCount, failCount, aboveThreshold);
    }

    private int calculateAndSave(
            Resumes resume, PublicJobPosting posting,
            ScorePublicRequest.ResumePayload resumePayload, List<Double> resumeVec
    ) {
        JobEmbeddingData jdData = getOrCreateJobEmbedding(posting);
        if (jdData == null) {
            throw new IllegalStateException("공고 임베딩 생성 불가: postingId=" + posting.getId());
        }

        ScorePublicRequest request = new ScorePublicRequest(
                posting.getId(),
                posting.getTitle(),
                posting.getCompanyName(),
                posting.getJobRole(),
                posting.getWorkExperience(),
                posting.getRecrutType(),
                posting.getApplyQualification(),
                posting.getApplicationMethod(),
                posting.getHtmlContent(),
                jdData.text(),
                resumePayload,
                toDoubleList(jdData.vector()),
                resumeVec
        );

        ScorePublicResponse response = aiScoringClient.scorePublic(request).block();
        if (response == null) {
            throw new IllegalStateException("AI 점수 응답 null: postingId=" + posting.getId());
        }

        int score = (int) Math.round(response.score());
        publicMatchScoreRepository.save(PublicMatchScore.builder()
                .member(resume.getMember())
                .resume(resume)
                .publicJobPosting(posting)
                .score(score)
                .scoreReason(response.scoreReason())
                .matchedSkills(toJson(response.matchedSkills()))
                .missingSkills(toJson(response.missingSkills()))
                .matchedCerts(toJson(response.matchedCerts()))
                .missingCerts(toJson(response.missingCerts()))
                .jobCluster(response.jobCluster())
                .resumeCluster(response.resumeCluster())
                .build());
        return score;
    }

    private ScorePublicRequest.ResumePayload buildResumePayload(Resumes resume) {
        List<String> resumeSkills = parseSkills(resume.getResumeSkills());
        int experienceYears = resolveExperienceYears(resume.getMember());
        String summary = resume.getExtractedText() != null && resume.getExtractedText().length() > MAX_SUMMARY_LENGTH
                ? resume.getExtractedText().substring(0, MAX_SUMMARY_LENGTH)
                : resume.getExtractedText();

        return new ScorePublicRequest.ResumePayload(
                resumeSkills,
                List.of(), // TODO: 이력서 자격증 파싱 미구현 — 항상 빈 값으로 전달
                experienceYears,
                "", // 이력서 희망직무 입력이 없어 비워둠 — skills/summary 기반으로 클러스터 분류
                summary
        );
    }

    private record JobEmbeddingData(float[] vector, String text) {
    }

    /**
     * 공고의 임베딩 벡터/텍스트를 조회하거나, 없으면 생성한다.
     *
     * @param posting 대상 공고
     * @return 임베딩 벡터+텍스트. 생성 실패 시 {@code null}
     */
    private JobEmbeddingData getOrCreateJobEmbedding(PublicJobPosting posting) {
        Optional<JobEmbedding> existing = jobEmbeddingRepository
                .findBySourceAndSourceId(JobSource.PUBLIC, posting.getId());
        if (existing.isPresent()) {
            JobEmbedding je = existing.get();
            return new JobEmbeddingData(je.getEmbedding(), je.getEmbeddingText());
        }

        try {
            embeddingService.embedPublicPosting(posting);
            return jobEmbeddingRepository
                    .findBySourceAndSourceId(JobSource.PUBLIC, posting.getId())
                    .map(je -> new JobEmbeddingData(je.getEmbedding(), je.getEmbeddingText()))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("공고 임베딩 생성 실패: postingId={}, error={}", posting.getId(), e.getMessage());
            return null;
        }
    }

    /** 온보딩 careerType을 경력 연수 정수로 변환한다. 연수 입력 필드 추가 시 교체 예정. */
    private int resolveExperienceYears(Member member) {
        return member.getCareerTypes().contains("경력직") ? 3 : 0;
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

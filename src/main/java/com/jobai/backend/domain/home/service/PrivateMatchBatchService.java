package com.jobai.backend.domain.home.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.ai.client.AiScoringClient;
import com.jobai.backend.domain.ai.dto.ScorePrivateRequest;
import com.jobai.backend.domain.ai.dto.ScorePrivateResponse;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.domain.search.entity.JobSource;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import com.jobai.backend.domain.search.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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

    private static final List<String> VALID_CATEGORIES = List.of(
            "백엔드", "프론트엔드", "풀스택", "모바일", "AI/ML",
            "데이터엔지니어링", "DevOps/인프라", "보안", "QA/테스트",
            "임베디드", "기타개발", "UX리서처", "UX/UI디자이너",
            "프로덕트디자이너", "웹디자이너", "PM/PO", "서비스기획"
    );

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
    public void scoreNewAndUpdatedPostings() {
        List<Resumes> activeResumes = resumesRepository.findAllActiveWithEmbedding();
        if (activeResumes.isEmpty()) {
            log.info("[배치점수] 활성 이력서가 없어 점수 산출 건너뜀");
            return;
        }

        List<PrivateJobPosting> activePostings =
                privateJobPostingRepository.findActiveByValidCategories(VALID_CATEGORIES);
        if (activePostings.isEmpty()) {
            log.info("[배치점수] 활성 공고가 없어 점수 산출 건너뜀");
            return;
        }

        Map<Long, PrivateJobPosting> postingMap = activePostings.stream()
                .collect(Collectors.toMap(PrivateJobPosting::getId, p -> p));

        log.info("[배치점수] 시작 — 이력서 {}개, 활성 공고 {}개", activeResumes.size(), activePostings.size());

        int totalNew = 0;
        int totalUpdated = 0;
        int totalFail = 0;

        for (Resumes resume : activeResumes) {
            try {
                int[] counts = self.scoreForResume(resume, postingMap);
                totalNew += counts[0];
                totalUpdated += counts[1];
                totalFail += counts[2];
            } catch (Exception e) {
                log.error("[배치점수] 이력서 {} 처리 중 오류: {}", resume.getId(), e.getMessage(), e);
            }
        }

        log.info("[배치점수] 완료 — 신규 점수 {}건, 변경 재산출 {}건, 실패 {}건",
                totalNew, totalUpdated, totalFail);
    }

    /**
     * 한 이력서에 대해 신규/변경 공고의 점수를 산출한다.
     *
     * <p>기존 점수를 공고ID별로 맵핑한 뒤, 활성 공고를 순회하며
     * 점수가 없으면 신규 산출, 공고 변경 시각이 점수 생성 시각보다 늦으면 재산출한다.</p>
     *
     * @param resume           점수를 산출할 이력서
     * @param activePostingMap 활성 공고 맵 (공고 ID → 엔티티)
     * @return {@code [신규 점수 수, 변경 재산출 수, 실패 수]} 배열
     */
    @Transactional
    public int[] scoreForResume(Resumes resume, Map<Long, PrivateJobPosting> activePostingMap) {
        Long resumeId = resume.getId();

        // 기존 점수 조회 → 공고ID별 점수 맵
        List<PrivateMatchScore> existingScores = privateMatchScoreRepository.findByResumeId(resumeId);
        Map<Long, PrivateMatchScore> scoreByPostingId = existingScores.stream()
                .collect(Collectors.toMap(
                        s -> s.getPrivateJobPosting().getId(),
                        s -> s
                ));

        List<String> resumeSkills = parseSkills(resume.getResumeSkills());
        List<Double> resumeVec = toDoubleList(resume.getEmbedding());
        int experienceYears = resolveExperienceYears(resume.getMember());

        int newCount = 0;
        int updatedCount = 0;
        int failCount = 0;

        for (PrivateJobPosting posting : activePostingMap.values()) {
            PrivateMatchScore existing = scoreByPostingId.get(posting.getId());

            if (existing == null) {
                // 신규: 점수 없음 → 산출
                try {
                    calculateAndSave(resume, posting, resumeVec, resumeSkills, experienceYears);
                    newCount++;
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
            }
            // else: 기존 점수 존재 + 변경 없음 → skip
        }

        if (newCount + updatedCount > 0) {
            log.info("[배치점수] resumeId={} — 신규 {}, 변경 {}, 실패 {}",
                    resumeId, newCount, updatedCount, failCount);
        }

        return new int[]{newCount, updatedCount, failCount};
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
    private void calculateAndSave(Resumes resume, PrivateJobPosting posting,
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

        privateMatchScoreRepository.save(PrivateMatchScore.builder()
                .member(resume.getMember())
                .resume(resume)
                .privateJobPosting(posting)
                .score((int) Math.round(response.score()))
                .scoreReason(response.scoreReason())
                .matchedSkills(toJson(response.matchedSkills()))
                .missingSkills(toJson(response.missingSkills()))
                .careerMet(response.careerMet())
                .modelVersion(response.modelVersion())
                .build());
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

    /** 온보딩 careerType을 경력 연수 정수로 변환한다. 연수 입력 필드 추가 시 교체 예정. */
    private int resolveExperienceYears(Member member) {
        if (member.getCareerType() == null) return 0;
        return switch (member.getCareerType()) {
            case "신입" -> 0;
            case "경력" -> 3;
            default -> 0;
        };
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

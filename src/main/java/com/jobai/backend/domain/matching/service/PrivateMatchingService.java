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
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.global.enums.JobSource;
import com.jobai.backend.global.enums.JobCategory;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import com.jobai.backend.domain.search.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 이력서 업로드 시 모든 활성 사기업 공고에 대해
 * AI 서버 /score/private를 호출하여 매칭 점수를 계산하고 저장하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateMatchingService {

    private final AiScoringClient aiScoringClient;
    private final PrivateJobPostingRepository privateJobPostingRepository;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final ResumesRepository resumesRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void calculateScores(Long resumeId) {
        Resumes resume = resumesRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            log.warn("이력서를 찾을 수 없음: resumeId={}", resumeId);
            return;
        }

        if (resume.getEmbedding() == null) {
            log.warn("이력서 임베딩이 없어 점수 계산 불가: resumeId={}", resumeId);
            return;
        }

        // 활성 공고 조회 (유효 카테고리만)
        List<PrivateJobPosting> activePostings = findActivePostings();
        if (activePostings.isEmpty()) {
            log.info("활성 공고가 없어 점수 계산 건너뜀: resumeId={}", resumeId);
            return;
        }

        Map<Long, PrivateMatchScore> existingScores = privateMatchScoreRepository.findByResumeId(resumeId).stream()
                .collect(Collectors.toMap(score -> score.getPrivateJobPosting().getId(), score -> score));

        List<String> resumeSkills = parseSkills(resume.getResumeSkills());
        List<Double> resumeVec = toDoubleList(resume.getEmbedding());
        int experienceYears = resolveExperienceYears(resume.getMember());

        int successCount = 0;
        int failCount = 0;

        for (PrivateJobPosting posting : activePostings) {
            try {
                float[] jdVec = getOrCreateJobEmbedding(posting);
                if (jdVec == null) {
                    failCount++;
                    continue;
                }

                String jdText = buildJdText(posting);
                ScorePrivateRequest request = new ScorePrivateRequest(
                        jdText, toDoubleList(jdVec), resumeVec, resumeSkills, experienceYears);

                ScorePrivateResponse response = aiScoringClient.scorePrivate(request).block();
                if (response == null) {
                    failCount++;
                    continue;
                }

                PrivateMatchScore replacement = PrivateMatchScore.builder()
                        .member(resume.getMember())
                        .resume(resume)
                        .privateJobPosting(posting)
                        .score((int) Math.round(response.score()))
                        .scoreReason(response.scoreReason())
                        .matchedSkills(toJson(response.matchedSkills()))
                        .missingSkills(toJson(response.missingSkills()))
                        .careerMet(response.careerMet())
                        .modelVersion(response.modelVersion())
                        .build();
                saveOrReplace(existingScores.get(posting.getId()), replacement);
                successCount++;
            } catch (Exception e) {
                log.warn("공고 점수 계산 실패: postingId={}, error={}", posting.getId(), e.getMessage());
                failCount++;
            }
        }

        log.info("매칭 점수 계산 결과: resumeId={}, 성공={}, 실패={}", resumeId, successCount, failCount);
    }

    private void saveOrReplace(PrivateMatchScore existing, PrivateMatchScore replacement) {
        if (existing != null) {
            privateMatchScoreRepository.delete(existing);
            privateMatchScoreRepository.flush();
        }
        privateMatchScoreRepository.save(replacement);
    }

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

    private List<PrivateJobPosting> findActivePostings() {
        return privateJobPostingRepository.findAll().stream()
                .filter(p -> !p.isClosed())
                .filter(p -> p.getJobCategory() != null)
                .filter(p -> JobCategory.matchTargetLabels().contains(p.getJobCategory()))
                .collect(Collectors.toList());
    }

    private String buildJdText(PrivateJobPosting posting) {
        String title = posting.getTitle() != null ? posting.getTitle() : "";
        String desc = posting.getDescription() != null ? posting.getDescription() : "";
        return title + "\n" + desc;
    }

    /** 온보딩 careerType을 experience_years 정수로 변환한다. 연수 입력 필드 추가 시 교체 예정. */
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

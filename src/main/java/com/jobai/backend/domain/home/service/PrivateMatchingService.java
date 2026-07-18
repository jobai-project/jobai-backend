package com.jobai.backend.domain.home.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.ai.client.AiScoringClient;
import com.jobai.backend.global.ai.dto.ScorePrivateRequest;
import com.jobai.backend.global.ai.dto.ScorePrivateResponse;
import com.jobai.backend.domain.jobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.jobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.global.enums.JobSource;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import com.jobai.backend.domain.search.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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

    private static final List<String> VALID_CATEGORIES = List.of(
            "백엔드", "프론트엔드", "풀스택", "모바일", "AI/ML",
            "데이터엔지니어링", "DevOps/인프라", "보안", "QA/테스트",
            "임베디드", "기타개발", "UX리서처", "UX/UI디자이너",
            "프로덕트디자이너", "웹디자이너", "PM/PO", "서비스기획"
    );

    /**
     * 비동기로 매칭 점수를 계산한다.
     * 이력서 업로드 트랜잭션 커밋 후 별도 스레드에서 실행된다.
     */
    @Async
    public void calculateScoresAsync(Long resumeId) {
        log.info("매칭 점수 계산 시작: resumeId={}", resumeId);
        try {
            calculateScores(resumeId);
            log.info("매칭 점수 계산 완료: resumeId={}", resumeId);
        } catch (Exception e) {
            log.error("매칭 점수 계산 중 오류: resumeId={}, error={}", resumeId, e.getMessage());
        }
    }

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

        // 기존 점수 삭제
        privateMatchScoreRepository.deleteByResumeId(resumeId);

        // 활성 공고 조회 (유효 카테고리만)
        List<PrivateJobPosting> activePostings = findActivePostings();
        if (activePostings.isEmpty()) {
            log.info("활성 공고가 없어 점수 계산 건너뜀: resumeId={}", resumeId);
            return;
        }

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
                successCount++;
            } catch (Exception e) {
                log.warn("공고 점수 계산 실패: postingId={}, error={}", posting.getId(), e.getMessage());
                failCount++;
            }
        }

        log.info("매칭 점수 계산 결과: resumeId={}, 성공={}, 실패={}", resumeId, successCount, failCount);
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
                .filter(p -> VALID_CATEGORIES.contains(p.getJobCategory()))
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

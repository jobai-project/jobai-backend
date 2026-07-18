package com.jobai.backend.domain.summary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.summary.dto.JobSummaryResponse;
import com.jobai.backend.domain.summary.dto.PrivateJobDetailResponse;
import com.jobai.backend.domain.summary.entity.JobPostingSummary;
import com.jobai.backend.domain.jobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.summary.repository.JobPostingSummaryRepository;
import com.jobai.backend.domain.jobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import com.jobai.backend.global.llm.LlmException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 사기업 공고 상세 조회 및 LLM 요약 서비스.
 *
 * <p>상세 조회 시 캐시된 요약이 있으면 함께 반환하고,
 * 요약 API 호출 시 온디맨드로 LLM 요약을 생성하여 DB에 캐싱한다.
 *
 * <p>LLM 호출은 트랜잭션 밖에서 수행하여 DB 커넥션 점유를 최소화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSummaryService {

    private final PrivateJobPostingRepository jobPostingRepository;
    private final JobPostingSummaryRepository summaryRepository;
    private final JobSummarizer jobSummarizer;
    private final ObjectMapper objectMapper;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;

    private static final int MIN_DESCRIPTION_LENGTH = 30;

    /**
     * 공고 상세 정보를 조회한다. 캐시된 요약이 있으면 함께 포함한다.
     * 로그인 사용자이고 활성 이력서가 있으면 매칭 점수와 점수 근거도 포함한다.
     *
     * @param id    공고 ID
     * @param email 인증된 사용자 이메일 (비로그인 시 null)
     * @return 공고 상세 응답 (요약, 매칭 점수 포함 가능)
     * @throws GeneralException 공고가 없으면 NOT_FOUND
     */
    @Transactional(readOnly = true)
    public PrivateJobDetailResponse getDetail(Long id, String email) {
        PrivateJobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        PrivateJobDetailResponse response;

        Optional<JobPostingSummary> cached = summaryRepository.findByJobPostingId(id);
        if (cached.isPresent()) {
            JobSummaryResponse.SummaryDetail detail = parseSummaryDetail(cached.get().getSummaryJson());
            if (detail != null) {
                response = PrivateJobDetailResponse.from(posting, detail);
            } else {
                response = PrivateJobDetailResponse.from(posting);
            }
        } else {
            response = PrivateJobDetailResponse.from(posting);
        }

        // 매칭 점수 로딩
        if (email != null && !"anonymousUser".equals(email)) {
            resumesRepository.findByMemberEmailAndIsActiveTrue(email).ifPresent(activeResume -> {
                List<PrivateMatchScore> scores = privateMatchScoreRepository
                        .findByResumeIdAndPrivateJobPostingIdIn(activeResume.getId(), List.of(id));
                if (!scores.isEmpty()) {
                    PrivateMatchScore score = scores.get(0);
                    response.setMatchScore(score.getScore());
                    response.setScoreReason(score.getScoreReason());
                }
            });
        }

        return response;
    }

    /**
     * 공고 요약을 반환한다. 캐시가 유효하면 캐시를 반환하고,
     * 없거나 소스가 변경되었으면 LLM으로 새로 생성하여 DB에 캐싱한다.
     *
     * <p>LLM 호출은 트랜잭션 밖에서 수행하여 DB 커넥션 점유를 최소화한다.
     *
     * @param id 공고 ID
     * @return 구조화된 요약 응답
     * @throws GeneralException NOT_FOUND(공고 없음), BAD_REQUEST(description 부족), SUMMARY_GENERATION_FAILED(LLM 실패)
     */
    public JobSummaryResponse getSummary(Long id) {
        // 1단계: 조회 (읽기 트랜잭션)
        PostingWithCache data = loadPostingAndCache(id);

        // 캐시가 유효하면 바로 반환
        if (data.cachedJson != null) {
            return buildResponse(data.posting, data.cachedJson);
        }

        // 2단계: LLM 호출 (트랜잭션 없음 — DB 커넥션 미점유)
        String summaryJson;
        try {
            summaryJson = jobSummarizer.summarize(data.posting.getDescription());
        } catch (LlmException e) {
            log.error("LLM 요약 생성 실패 (jobPostingId={}): {}", id, e.getMessage());
            throw new GeneralException(GeneralErrorCode.SUMMARY_GENERATION_FAILED);
        } catch (Exception e) {
            log.error("요약 생성 중 예외 (jobPostingId={}): {}", id, e.getMessage());
            throw new GeneralException(GeneralErrorCode.SUMMARY_GENERATION_FAILED);
        }

        // 3단계: 저장 (쓰기 트랜잭션, 중복키 시 기존 캐시 반환)
        String savedJson = saveOrFallback(id, summaryJson, data.sourceUpdatedAt);

        return buildResponse(data.posting, savedJson);
    }

    /**
     * 공고와 캐시를 조회하고 유효성을 검증한다.
     * 캐시가 유효하면 cachedJson에 값이 채워지고, 재생성이 필요하면 null이다.
     */
    @Transactional(readOnly = true)
    public PostingWithCache loadPostingAndCache(Long id) {
        PrivateJobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        String description = posting.getDescription();
        if (description == null || description.length() < MIN_DESCRIPTION_LENGTH) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST,
                    "공고 설명이 없거나 너무 짧아 요약할 수 없습니다.");
        }

        LocalDateTime sourceUpdatedAt = posting.getUpdatedAt() != null
                ? posting.getUpdatedAt() : posting.getCreatedAt();

        Optional<JobPostingSummary> cached = summaryRepository.findByJobPostingId(id);
        if (cached.isPresent() && !isStale(cached.get(), sourceUpdatedAt)) {
            return new PostingWithCache(posting, cached.get().getSummaryJson(), sourceUpdatedAt);
        }

        return new PostingWithCache(posting, null, sourceUpdatedAt);
    }

    /**
     * 요약 결과를 DB에 저장하거나 갱신한다.
     * 동시 첫 요청으로 중복키 예외 발생 시, 먼저 저장된 캐시를 재조회하여 반환한다.
     *
     * @return 실제 저장된(또는 이미 존재하던) 요약 JSON
     */
    private String saveOrFallback(Long jobPostingId, String summaryJson, LocalDateTime sourceUpdatedAt) {
        try {
            saveSummary(jobPostingId, summaryJson, sourceUpdatedAt);
            return summaryJson;
        } catch (DataIntegrityViolationException e) {
            log.info("동시 요약 요청으로 중복 저장 감지 (jobPostingId={}), 기존 캐시 반환", jobPostingId);
            return summaryRepository.findByJobPostingId(jobPostingId)
                    .map(JobPostingSummary::getSummaryJson)
                    .orElse(summaryJson);
        }
    }

    /** 요약 결과를 DB에 저장하거나 갱신한다. */
    @Transactional
    public void saveSummary(Long jobPostingId, String summaryJson, LocalDateTime sourceUpdatedAt) {
        Optional<JobPostingSummary> existing = summaryRepository.findByJobPostingId(jobPostingId);
        if (existing.isPresent()) {
            existing.get().updateSummary(summaryJson, sourceUpdatedAt);
        } else {
            JobPostingSummary summary = JobPostingSummary.builder()
                    .jobPostingId(jobPostingId)
                    .summaryJson(summaryJson)
                    .sourceUpdatedAt(sourceUpdatedAt)
                    .build();
            summaryRepository.save(summary);
        }
    }

    /** 조회 결과를 담는 내부 레코드. */
    record PostingWithCache(PrivateJobPosting posting, String cachedJson, LocalDateTime sourceUpdatedAt) {}

    /** 캐시된 요약이 소스 변경으로 인해 무효화되었는지 판별한다. */
    private boolean isStale(JobPostingSummary cached, LocalDateTime sourceUpdatedAt) {
        return sourceUpdatedAt.isAfter(cached.getSourceUpdatedAt());
    }

    /** 요약 JSON 문자열을 SummaryDetail 객체로 파싱한다. 실패 시 null 반환. */
    private JobSummaryResponse.SummaryDetail parseSummaryDetail(String summaryJson) {
        try {
            JsonNode root = objectMapper.readTree(summaryJson);
            return JobSummaryResponse.SummaryDetail.builder()
                    .techStack(readStringList(root, "techStack"))
                    .responsibilities(readStringList(root, "responsibilities"))
                    .qualifications(readStringList(root, "qualifications"))
                    .preferredQualifications(readStringList(root, "preferredQualifications"))
                    .build();
        } catch (Exception e) {
            log.warn("요약 JSON 파싱 실패 (상세 조회): {}", e.getMessage());
            return null;
        }
    }

    /** 공고 + 요약 JSON으로 JobSummaryResponse를 생성한다. */
    private JobSummaryResponse buildResponse(PrivateJobPosting posting, String summaryJson) {
        try {
            JobSummaryResponse.SummaryDetail detail = parseSummaryDetail(summaryJson);
            if (detail == null) {
                throw new GeneralException(GeneralErrorCode.SUMMARY_GENERATION_FAILED);
            }

            return JobSummaryResponse.builder()
                    .jobPostingId(posting.getId())
                    .title(posting.getTitle())
                    .company(posting.getCompany())
                    .applyUrl(posting.getApplyUrl())
                    .summary(detail)
                    .build();
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("요약 JSON 파싱 실패: {}", e.getMessage());
            throw new GeneralException(GeneralErrorCode.SUMMARY_GENERATION_FAILED);
        }
    }

    /** JSON 노드에서 문자열 배열을 읽는다. 필드가 없거나 배열이 아니면 빈 리스트. */
    private List<String> readStringList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            list.add(item.asText());
        }
        return list;
    }
}

package com.jobai.backend.domain.crawler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.dto.JobSummaryResponse;
import com.jobai.backend.domain.crawler.dto.PrivateJobDetailResponse;
import com.jobai.backend.domain.crawler.entity.JobPostingSummary;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.repository.JobPostingSummaryRepository;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.crawler.summary.JobSummarizer;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import com.jobai.backend.global.llm.LlmException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSummaryService {

    private final PrivateJobPostingRepository jobPostingRepository;
    private final JobPostingSummaryRepository summaryRepository;
    private final JobSummarizer jobSummarizer;
    private final ObjectMapper objectMapper;

    private static final int MIN_DESCRIPTION_LENGTH = 30;

    @Transactional(readOnly = true)
    public PrivateJobDetailResponse getDetail(Long id) {
        PrivateJobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        return PrivateJobDetailResponse.from(posting);
    }

    @Transactional
    public JobSummaryResponse getSummary(Long id) {
        PrivateJobPosting posting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        String description = posting.getDescription();
        if (description == null || description.length() < MIN_DESCRIPTION_LENGTH) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST,
                    "공고 설명이 없거나 너무 짧아 요약할 수 없습니다.");
        }

        Optional<JobPostingSummary> cached = summaryRepository.findByJobPostingId(id);
        LocalDateTime sourceUpdatedAt = posting.getUpdatedAt() != null
                ? posting.getUpdatedAt() : posting.getCreatedAt();

        // 캐시가 유효하면 바로 반환
        if (cached.isPresent() && !isStale(cached.get(), sourceUpdatedAt)) {
            return buildResponse(posting, cached.get().getSummaryJson());
        }

        // LLM 요약 생성
        String summaryJson;
        try {
            summaryJson = jobSummarizer.summarize(description);
        } catch (LlmException e) {
            log.error("LLM 요약 생성 실패 (jobPostingId={}): {}", id, e.getMessage());
            throw new GeneralException(GeneralErrorCode.SUMMARY_GENERATION_FAILED);
        } catch (Exception e) {
            log.error("요약 생성 중 예외 (jobPostingId={}): {}", id, e.getMessage());
            throw new GeneralException(GeneralErrorCode.SUMMARY_GENERATION_FAILED);
        }

        // 캐시 저장 또는 업데이트
        if (cached.isPresent()) {
            cached.get().updateSummary(summaryJson, sourceUpdatedAt);
        } else {
            JobPostingSummary summary = JobPostingSummary.builder()
                    .jobPostingId(id)
                    .summaryJson(summaryJson)
                    .sourceUpdatedAt(sourceUpdatedAt)
                    .build();
            summaryRepository.save(summary);
        }

        return buildResponse(posting, summaryJson);
    }

    private boolean isStale(JobPostingSummary cached, LocalDateTime sourceUpdatedAt) {
        return sourceUpdatedAt.isAfter(cached.getSourceUpdatedAt());
    }

    private JobSummaryResponse buildResponse(PrivateJobPosting posting, String summaryJson) {
        try {
            JsonNode root = objectMapper.readTree(summaryJson);
            JobSummaryResponse.SummaryDetail detail = JobSummaryResponse.SummaryDetail.builder()
                    .techStack(readStringList(root, "techStack"))
                    .responsibilities(readStringList(root, "responsibilities"))
                    .qualifications(readStringList(root, "qualifications"))
                    .preferredQualifications(readStringList(root, "preferredQualifications"))
                    .build();

            return JobSummaryResponse.builder()
                    .jobPostingId(posting.getId())
                    .title(posting.getTitle())
                    .company(posting.getCompany())
                    .applyUrl(posting.getApplyUrl())
                    .summary(detail)
                    .build();
        } catch (Exception e) {
            log.error("요약 JSON 파싱 실패: {}", e.getMessage());
            throw new GeneralException(GeneralErrorCode.SUMMARY_GENERATION_FAILED);
        }
    }

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

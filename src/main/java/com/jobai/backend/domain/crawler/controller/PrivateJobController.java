package com.jobai.backend.domain.crawler.controller;

import com.jobai.backend.domain.crawler.dto.JobSummaryResponse;
import com.jobai.backend.domain.crawler.dto.PrivateJobDetailResponse;
import com.jobai.backend.domain.crawler.service.JobSummaryService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사기업 공고 상세 조회 및 LLM 요약 API 컨트롤러. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/private-jobs")
public class PrivateJobController {

    private final JobSummaryService jobSummaryService;

    /**
     * 공고 상세 조회. 원문 description을 포함하며, 캐시된 요약이 있으면 함께 반환한다.
     *
     * @param id 공고 ID
     * @return 공고 상세 정보 (요약 포함 가능)
     */
    @GetMapping("/{id}")
    public ApiResponse<PrivateJobDetailResponse> getJobDetail(@PathVariable Long id) {
        PrivateJobDetailResponse response = jobSummaryService.getDetail(id);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    /**
     * 공고 요약 조회. 첫 호출 시 LLM으로 요약을 생성하고 DB에 캐싱한다.
     *
     * @param id 공고 ID
     * @return 구조화된 요약 (techStack, responsibilities, qualifications, preferredQualifications)
     */
    @GetMapping("/{id}/summary")
    public ApiResponse<JobSummaryResponse> getJobSummary(@PathVariable Long id) {
        JobSummaryResponse response = jobSummaryService.getSummary(id);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

package com.jobai.backend.domain.privatejob.controller;

import com.jobai.backend.domain.privatejob.dto.JobSummaryResponse;
import com.jobai.backend.domain.privatejob.dto.PrivateJobDetailResponse;
import com.jobai.backend.domain.privatejob.service.PrivateJobDetailService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사기업 공고 상세 조회 및 LLM 요약 API 컨트롤러. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/private-jobs")
public class PrivateJobController implements PrivateJobControllerDocs {

    private final PrivateJobDetailService privateJobDetailService;

    @Override
    @GetMapping("/{id}")
    public ApiResponse<PrivateJobDetailResponse> getJobDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal String email) {
        PrivateJobDetailResponse response = privateJobDetailService.getDetail(id, email);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @Override
    @GetMapping("/{id}/summary")
    public ApiResponse<JobSummaryResponse> getJobSummary(@PathVariable Long id) {
        JobSummaryResponse response = privateJobDetailService.getSummary(id);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

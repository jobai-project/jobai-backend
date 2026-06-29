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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/private-jobs")
public class PrivateJobController {

    private final JobSummaryService jobSummaryService;

    @GetMapping("/{id}")
    public ApiResponse<PrivateJobDetailResponse> getJobDetail(@PathVariable Long id) {
        PrivateJobDetailResponse response = jobSummaryService.getDetail(id);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @GetMapping("/{id}/summary")
    public ApiResponse<JobSummaryResponse> getJobSummary(@PathVariable Long id) {
        JobSummaryResponse response = jobSummaryService.getSummary(id);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

package com.jobai.backend.domain.search.controller;

import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.service.JobSearchService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Job Search", description = "자연어 공고 검색 API")
public class JobSearchController {

    private final JobSearchService jobSearchService;

    @GetMapping("/jobs")
    @Operation(summary = "공고 검색", description = "자연어 입력으로 Private + Public 공고를 통합 검색합니다.")
    public ApiResponse<JobSearchResponse> searchJobs(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        JobSearchResponse response = jobSearchService.search(query, page, size);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

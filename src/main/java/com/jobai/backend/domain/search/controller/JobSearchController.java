package com.jobai.backend.domain.search.controller;

import com.jobai.backend.domain.search.dto.JobSearchRequest;
import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.service.JobSearchService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class JobSearchController implements JobSearchControllerDocs {

    private final JobSearchService jobSearchService;

    @GetMapping("/jobs")
    public ApiResponse<JobSearchResponse> searchJobs(
            @Valid @ModelAttribute JobSearchRequest request,
            @AuthenticationPrincipal String email) {
        JobSearchResponse response = jobSearchService.search(
                request.query(), request.page(), request.size(), email);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

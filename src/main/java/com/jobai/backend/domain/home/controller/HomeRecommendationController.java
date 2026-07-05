package com.jobai.backend.domain.home.controller;

import com.jobai.backend.domain.home.dto.HomeRecommendationResponse;
import com.jobai.backend.domain.home.service.HomeRecommendationService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class HomeRecommendationController implements HomeRecommendationControllerDocs {

    private final HomeRecommendationService homeRecommendationService;

    @GetMapping("/recommended-jobs")
    public ApiResponse<HomeRecommendationResponse> getRecommendedJobs(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) List<String> companyTypes,
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) List<String> employmentTypes,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "18") int size
    ) {
        HomeRecommendationResponse response = homeRecommendationService.getRecommendedJobs(
                email, companyTypes, locations, employmentTypes, offset, size);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

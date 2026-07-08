package com.jobai.backend.domain.publicInstitution.controller;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobPostingDetailResponse;
import com.jobai.backend.domain.publicInstitution.service.PublicJobPostingService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public-jobs")
public class PublicJobPostingController implements PublicJobPostingControllerDocs {

    private final PublicJobPostingService publicJobPostingService;

    @GetMapping("/{id}")
    public ApiResponse<PublicJobPostingDetailResponse> getPublicJobDetail(@PathVariable Long id) {
        PublicJobPostingDetailResponse response = publicJobPostingService.getDetail(id);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

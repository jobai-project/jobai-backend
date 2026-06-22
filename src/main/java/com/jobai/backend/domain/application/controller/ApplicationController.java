package com.jobai.backend.domain.application.controller;

import com.jobai.backend.domain.application.dto.ApplicationRequestDTO;
import com.jobai.backend.domain.application.service.ApplicationService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ApiResponse<String> createApplication(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody ApplicationRequestDTO.CreateApplicationDTO request
    ) {
        applicationService.addApplication(email, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, "입사 지원 현황에 성공적으로 추가되었습니다.");
    }
}

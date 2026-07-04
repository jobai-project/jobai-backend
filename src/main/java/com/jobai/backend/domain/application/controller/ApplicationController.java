package com.jobai.backend.domain.application.controller;

import com.jobai.backend.domain.application.dto.ApplicationRequestDTO;
import com.jobai.backend.domain.application.dto.ApplicationResponseDTO;
import com.jobai.backend.domain.application.service.ApplicationService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/applications")
public class ApplicationController implements ApplicationControllerDocs {

    private final ApplicationService applicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // ApiResponse는 ResponseEntity가 아니므로 명시하지 않으면 200이 반환됨
    public ApiResponse<ApplicationResponseDTO.CreateResultDTO> createApplication(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody ApplicationRequestDTO.CreateApplicationDTO request
    ) {
        Long applicationId = applicationService.addApplication(email, request);

        ApplicationResponseDTO.CreateResultDTO response = ApplicationResponseDTO.CreateResultDTO.builder()
                .applicationId(applicationId)
                .build();
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, response);
    }

    @PatchMapping("/{applicationId}") // 수정을 원하는 공고 고유 ID를 경로로 받음
    public ApiResponse<String> updateApplication(
            @AuthenticationPrincipal String email,
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationRequestDTO.UpdateApplicationDTO request
    ) {
        applicationService.modifyApplication(email, applicationId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "입사 지원 현황이 성공적으로 수정되었습니다.");
    }

    @DeleteMapping("/{applicationId}") // 삭제 대상을 URI 경로 변수로 받음
    public ApiResponse<String> deleteApplication(
            @AuthenticationPrincipal String email,
            @PathVariable Long applicationId
    ) {
        applicationService.removeApplication(email, applicationId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "입사 지원 기록이 성공적으로 삭제되었습니다.");
    }

    @GetMapping
    public ApiResponse<ApplicationResponseDTO.ApplicationListDTO> getApplications(
            @AuthenticationPrincipal String email
    ) {
        ApplicationResponseDTO.ApplicationListDTO response = applicationService.getApplicationList(email);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @GetMapping("/summary") // 요약 대시보드 API
    public ApiResponse<ApplicationResponseDTO.ApplicationSummaryDTO> getApplicationSummary(
            @AuthenticationPrincipal String email
    ) {
        ApplicationResponseDTO.ApplicationSummaryDTO response = applicationService.getApplicationSummary(email);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @GetMapping("/upcoming") // 다가오는 일정 전용 API
    public ApiResponse<ApplicationResponseDTO.UpcomingScheduleListDTO> getUpcomingSchedules(
            @AuthenticationPrincipal String email
    ) {
        ApplicationResponseDTO.UpcomingScheduleListDTO response = applicationService.getUpcomingSchedules(email);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

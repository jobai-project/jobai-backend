package com.jobai.backend.domain.application.controller;

import com.jobai.backend.domain.application.dto.ApplicationRequestDTO;
import com.jobai.backend.domain.application.dto.ApplicationResponseDTO;
import com.jobai.backend.domain.application.service.ApplicationService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/applications")
public class ApplicationController implements ApplicationControllerDocs {

    private final ApplicationService applicationService;

    @PostMapping
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

    @DeleteMapping("/{applicationId}") // 💡 삭제 대상을 URI 경로 변수로 받습니다.
    public ApiResponse<String> deleteApplication(
            @AuthenticationPrincipal String email,
            @PathVariable Long applicationId
    ) {
        applicationService.removeApplication(email, applicationId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "입사 지원 기록이 성공적으로 삭제되었습니다.");
    }

    @GetMapping // 💡 목록 조회의 RESTful 명세 규격 규칙 적용
    public ApiResponse<ApplicationResponseDTO.ApplicationListDTO> getApplications(
            @AuthenticationPrincipal String email
    ) {
        ApplicationResponseDTO.ApplicationListDTO response = applicationService.getApplicationList(email);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}

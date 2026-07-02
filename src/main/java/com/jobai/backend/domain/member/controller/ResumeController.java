package com.jobai.backend.domain.member.controller;

import com.jobai.backend.domain.member.dto.ResumeResponseDTO;
import com.jobai.backend.domain.member.service.ResumeService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/resumes")
public class ResumeController implements ResumeControllerDocs {

    private final ResumeService resumeService;

    @GetMapping
    public ApiResponse<ResumeResponseDTO.ResumeListDTO> getResumes(
            @AuthenticationPrincipal String email
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, resumeService.getResumes(email));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED) // ApiResponse는 ResponseEntity가 아니므로 명시하지 않으면 200이 반환됨
    public ApiResponse<ResumeResponseDTO.UploadResultDTO> uploadResume(
            @AuthenticationPrincipal String email,
            @RequestPart("file") MultipartFile file
    ) {
        Long resumeId = resumeService.uploadResume(email, file);
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED,
                ResumeResponseDTO.UploadResultDTO.builder().resumeId(resumeId).build());
    }

    @PatchMapping("/{resumeId}/activate")
    public ApiResponse<String> activateResume(
            @AuthenticationPrincipal String email,
            @PathVariable Long resumeId
    ) {
        resumeService.activateResume(email, resumeId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "이력서가 활성화되었습니다.");
    }

    @DeleteMapping("/{resumeId}")
    public ApiResponse<String> deleteResume(
            @AuthenticationPrincipal String email,
            @PathVariable Long resumeId
    ) {
        resumeService.deleteResume(email, resumeId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "이력서가 삭제되었습니다.");
    }
}

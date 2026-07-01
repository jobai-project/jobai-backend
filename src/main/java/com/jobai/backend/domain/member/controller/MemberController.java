package com.jobai.backend.domain.member.controller;

import com.jobai.backend.domain.member.dto.MemberRequestDTO;
import com.jobai.backend.domain.member.dto.MemberResponseDTO;
import com.jobai.backend.domain.member.service.MemberService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController implements MemberControllerDocs {

    private final MemberService memberService;

    @PutMapping("/me/job-preferences")
    public ApiResponse<String> updateJobPreferences(
            @AuthenticationPrincipal String email, // 💡 시큐리티 필터가 저장한 email(String)을 그대로 받습니다.
            @RequestBody MemberRequestDTO.UpdateJobPreferenceDTO request
    ) {
        // 식별자로 id 대신 email을 서비스 레이어로 넘겨줍니다.
        memberService.updateMyJobPreferences(email, request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "공고 조건 설정이 성공적으로 변경되었습니다.");
    }

    @GetMapping("/me")
    public ApiResponse<MemberResponseDTO.MyPageDTO> getMyPage(
            @AuthenticationPrincipal String email) {

        MemberResponseDTO.MyPageDTO myPageData = memberService.getMyPageData(email);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, myPageData);
    }

    @PatchMapping("/me/name") // 💡 특정 필드 수정이므로 PATCH 활용
    public ApiResponse<String> updateName(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody MemberRequestDTO.UpdateNameDTO request
    ) {
        memberService.updateMemberName(email, request.getName());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "이름이 성공적으로 수정되었습니다.");
    }

    @PatchMapping("/me/onboarding/basic-info")
    public ApiResponse<String> updateOnboardingBasicInfo(
            @AuthenticationPrincipal String email,
            @RequestBody MemberRequestDTO.UpdateBasicInfoDTO request
    ) {
        memberService.updateOnboardingBasicInfo(email, request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "기본 정보가 저장되었습니다.");
    }

    @PatchMapping("/me/onboarding/job-category")
    public ApiResponse<String> updateOnboardingJobCategory(
            @AuthenticationPrincipal String email,
            @RequestBody MemberRequestDTO.UpdateJobCategoryDTO request
    ) {
        memberService.updateOnboardingJobCategory(email, request);

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "희망 직무가 저장되었습니다.");
    }
}
package com.jobai.backend.domain.member.controller;

import com.jobai.backend.domain.member.dto.MemberRequestDTO;
import com.jobai.backend.domain.member.service.MemberService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

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
}
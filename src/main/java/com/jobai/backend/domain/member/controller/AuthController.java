package com.jobai.backend.domain.member.controller;

import com.jobai.backend.domain.member.dto.AuthResponse;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.service.MemberService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;

    @Operation(summary = "로그아웃", description = "accessToken 쿠키를 삭제하여 로그아웃 처리를 합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", "")
                .path("/")
                .httpOnly(true)
                .secure(false)
                .maxAge(0) // 쿠키 즉시 만료
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }

    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자의 정보를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<AuthResponse.MemberInfo> getMyInfo(@AuthenticationPrincipal String email) {
        if (email == null) {
            return ApiResponse.onFailure(GeneralErrorCode.NOT_FOUND, null);
        }

        Optional<Member> member = memberService.findByEmail(email);
        return member.map(m -> ApiResponse.onSuccess(GeneralSuccessCode.OK,
                        AuthResponse.MemberInfo.builder()
                                .email(m.getEmail())
                                .name(m.getName())
                                .build()))
                .orElse(ApiResponse.onSuccess(GeneralSuccessCode.OK, null));
    }
}

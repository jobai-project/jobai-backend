package com.jobai.backend.domain.auth.controller;

import com.jobai.backend.domain.auth.dto.AuthResponse;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.service.MemberService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import com.jobai.backend.global.auth.CookieProvider;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final MemberService memberService;
    private final CookieProvider cookieProvider;

    @GetMapping("/login/google")
    public ApiResponse<AuthResponse.LoginUrl> getGoogleLoginUrl() {
        // Spring Security 기본 OAuth2 인증 시작 경로
        String url = "/oauth2/authorization/google";
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, 
                AuthResponse.LoginUrl.builder().googleLoginUrl(url).build());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {

        ResponseCookie cookie = cookieProvider.createEmptyAccessTokenCookie();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse.MemberInfo> getMyInfo(@AuthenticationPrincipal String email) {
        if (email == null) {
            return ApiResponse.onFailure(GeneralErrorCode.UNAUTHORIZED, null);
        }

        Optional<Member> member = memberService.findByEmail(email);
        return member.map(m -> ApiResponse.onSuccess(GeneralSuccessCode.OK,
                        AuthResponse.MemberInfo.builder()
                                .email(m.getEmail())
                                .name(m.getName())
                                .build()))
                .orElse(ApiResponse.onFailure(GeneralErrorCode.NOT_FOUND, null));
    }
}

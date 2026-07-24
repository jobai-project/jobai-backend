package com.jobai.backend.domain.home.controller;

import com.jobai.backend.domain.home.service.HomeRecommendationService;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import com.jobai.backend.global.apiPayload.handler.GeneralExceptionAdvice;
import com.jobai.backend.global.auth.JwtProvider;
import com.jobai.backend.global.auth.CookieProvider;
import com.jobai.backend.global.auth.CustomOAuth2UserService;
import com.jobai.backend.global.auth.FrontendRedirectUriResolver;
import com.jobai.backend.global.auth.OAuth2SuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeRecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GeneralExceptionAdvice.class)
class HomeRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeRecommendationService homeRecommendationService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private CookieProvider cookieProvider;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private FrontendRedirectUriResolver frontendRedirectUriResolver;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    @DisplayName("잘못된 페이지 요청은 공통 400 응답으로 반환한다")
    void returnsBadRequestForInvalidPagination() throws Exception {
        when(homeRecommendationService.getRecommendedJobs(
                nullable(String.class), nullable(java.util.List.class), nullable(java.util.List.class),
                nullable(java.util.List.class), eq(-1), eq(18)))
                .thenThrow(new GeneralException(
                        GeneralErrorCode.BAD_REQUEST,
                        "offset은 0 이상, size는 1 이상 100 이하여야 합니다."
                ));

        mockMvc.perform(get("/api/v1/home/recommended-jobs")
                        .param("offset", "-1")
                        .param("size", "18"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
    }
}

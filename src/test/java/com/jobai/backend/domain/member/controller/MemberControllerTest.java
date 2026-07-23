package com.jobai.backend.domain.member.controller;

import com.jobai.backend.domain.member.service.MemberService;
import com.jobai.backend.domain.member.service.MemberWithdrawalService;
import com.jobai.backend.domain.notification.service.NotificationSettingsService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.auth.CookieProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberControllerTest {

    @Test
    void withdrawDeletesMemberAndExpiresAccessTokenCookie() {
        MemberService memberService = mock(MemberService.class);
        NotificationSettingsService notificationSettingsService = mock(NotificationSettingsService.class);
        MemberWithdrawalService memberWithdrawalService = mock(MemberWithdrawalService.class);
        CookieProvider cookieProvider = mock(CookieProvider.class);
        when(cookieProvider.createEmptyAccessTokenCookie()).thenReturn(ResponseCookie.from("accessToken", "")
                .path("/")
                .maxAge(0)
                .build());
        MemberController controller = new MemberController(
                memberService, notificationSettingsService, memberWithdrawalService, cookieProvider
        );
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<String> response = controller.withdraw("member@example.com", servletResponse);

        verify(memberWithdrawalService).withdraw("member@example.com");
        assertThat(servletResponse.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(response.isSuccess()).isTrue();
    }
}

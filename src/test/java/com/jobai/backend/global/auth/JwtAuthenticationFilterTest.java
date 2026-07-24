package com.jobai.backend.global.auth;

import com.jobai.backend.domain.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, memberRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyWhenTokenOwnerStillExists() throws Exception {
        MockHttpServletRequest request = requestWithAccessToken();
        when(jwtProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtProvider.getEmailFromToken("valid-token")).thenReturn("member@example.com");
        when(memberRepository.existsByEmail("member@example.com")).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("member@example.com");
        verify(memberRepository).existsByEmail("member@example.com");
    }

    @Test
    void doesNotAuthenticateDeletedMemberToken() throws Exception {
        MockHttpServletRequest request = requestWithAccessToken();
        when(jwtProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtProvider.getEmailFromToken("valid-token")).thenReturn("deleted@example.com");
        when(memberRepository.existsByEmail("deleted@example.com")).thenReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(memberRepository).existsByEmail("deleted@example.com");
    }

    private MockHttpServletRequest requestWithAccessToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieProvider.ACCESS_TOKEN_COOKIE_NAME, "valid-token"));
        return request;
    }
}

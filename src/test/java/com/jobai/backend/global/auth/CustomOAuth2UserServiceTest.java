package com.jobai.backend.global.auth;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    void existingMemberKeepsServiceNameOnLogin() {
        Member existingMember = Member.builder()
                .email("member@jobai.com")
                .name("Service Name")
                .provider("google")
                .providerId("google-id")
                .build();
        when(memberRepository.findByEmail("member@jobai.com"))
                .thenReturn(Optional.of(existingMember));

        Member result = customOAuth2UserService.saveIfNew(
                "member@jobai.com", "Google Name", "google", "google-id");

        assertThat(result).isSameAs(existingMember);
        assertThat(existingMember.getName()).isEqualTo("Service Name");
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void newMemberStoresGoogleProfileOnFirstLogin() {
        when(memberRepository.findByEmail("new@jobai.com")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member result = customOAuth2UserService.saveIfNew(
                "new@jobai.com", "Google Name", "google", "google-id");

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member savedMember = captor.getValue();
        assertThat(result).isSameAs(savedMember);
        assertThat(savedMember.getEmail()).isEqualTo("new@jobai.com");
        assertThat(savedMember.getName()).isEqualTo("Google Name");
        assertThat(savedMember.getProvider()).isEqualTo("google");
        assertThat(savedMember.getProviderId()).isEqualTo("google-id");
    }
}

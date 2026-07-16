package com.jobai.backend.domain.member.service;

import com.jobai.backend.domain.member.dto.MemberResponseDTO;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    @Test
    void myPageReturnsAllSelectedCareerTypes() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        Member member = Member.builder()
                .email("test@jobai.com")
                .careerTypes(List.of("신입", "계약직"))
                .build();
        when(memberRepository.findByEmail("test@jobai.com")).thenReturn(Optional.of(member));

        MemberResponseDTO.MyPageDTO result = new MemberService(memberRepository)
                .getMyPageData("test@jobai.com");

        assertThat(result.getJobPreference().getCareerType())
                .containsExactly("신입", "계약직");
    }

    @Test
    void updateBasicInfoRemovesDuplicateCareerTypes() {
        Member member = Member.builder().email("test@jobai.com").build();

        member.updateBasicInfo(List.of("신입", "신입", "계약직"), List.of("서울"));

        assertThat(member.getCareerTypes()).containsExactly("신입", "계약직");
    }
}
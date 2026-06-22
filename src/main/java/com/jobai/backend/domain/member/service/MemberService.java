package com.jobai.backend.domain.member.service;

import com.jobai.backend.domain.member.dto.MemberRequestDTO;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.BaseErrorCode;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.jobai.backend.global.apiPayload.code.GeneralErrorCode.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    @Transactional
    public void updateMyJobPreferences(String email, MemberRequestDTO.UpdateJobPreferenceDTO request) {
        // 1. findByEmail을 사용해 유저를 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));

        // 2. 엔티티 내부 비즈니스 로직(통 업데이트) 호출
        member.updateJobPreferences(
                request.getCareerType(),
                request.getJobCategories(),
                request.getLocations()
        );

        // 트랜잭션이 종료되면서 Dirty Checking에 의해 수정 사항이 자동으로 DB에 반영됩니다.
    }
}

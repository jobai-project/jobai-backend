package com.jobai.backend.domain.application.service;

import com.jobai.backend.domain.application.dto.ApplicationRequestDTO;
import com.jobai.backend.domain.application.entity.Application;
import com.jobai.backend.domain.application.repository.ApplicationRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void addApplication(String email, ApplicationRequestDTO.CreateApplicationDTO request) {
        // 1. 유저 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));

        // 2. 빌더를 활용해 엔티티 생성 (외래키 주인 객체에 member 주입 필수!)
        Application application = Application.builder()
                .member(member)
                .companyName(request.getCompanyName())
                .jobTitle(request.getJobTitle())
                .status(request.getStatus())
                .appliedAt(request.getAppliedAt())
                .interviewAt(request.getInterviewAt())
                .memo(request.getMemo())
                .build();

        // 3. 저장
        applicationRepository.save(application);
    }
}
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
    public Long addApplication(String email, ApplicationRequestDTO.CreateApplicationDTO request) {
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
        Application savedApplication = applicationRepository.save(application);

        return savedApplication.getId();
    }

    @Transactional // 더티체크를 위한 쓰기 트랜잭션
    public void modifyApplication(String email, Long applicationId, ApplicationRequestDTO.UpdateApplicationDTO request) {
        // 1. 수정 타겟 공고 조회
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 지원 현황을 찾을 수 없습니다."));

        // 2. 로그인한 유저가 이 공고의 진짜 주인인지 검증
        if (!application.getMember().getEmail().equals(email)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 현황을 수정할 권한이 없습니다.");
        }

        // 3. 엔티티 내에 선언한 수정을 전담하는 도메인 로직에 데이터 위임
        application.update(
                request.getCompanyName(),
                request.getJobTitle(),
                request.getStatus(),
                request.getAppliedAt(),
                request.getInterviewAt(),
                request.getMemo()
        );
    }
}
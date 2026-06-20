package com.jobai.backend.domain.member.service;

import com.jobai.backend.domain.member.dto.MemberRequestDTO;
import com.jobai.backend.domain.member.dto.MemberResponseDTO;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.PreferredJob;
import com.jobai.backend.domain.member.entity.PreferredRegion;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.BaseErrorCode;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    }
    
    // 마이페이지의 모든 정보를 조회하는 함수
    public MemberResponseDTO.MyPageDTO getMyPageData(String email) {
        // 1. 이메일 기반 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));

        // 2. 프로필 정보 조립
        MemberResponseDTO.ProfileInfo profile = MemberResponseDTO.ProfileInfo.builder()
                .name(member.getName())
                .email(member.getEmail())
                .build();

        // 3. 선호 조건 정보 조립
        MemberResponseDTO.JobPreferenceInfo jobPreference = MemberResponseDTO.JobPreferenceInfo.builder()
                .careerType(member.getCareerType()) // TODO: 온보딩에서 신입/경력(CareerType) 넣어줘야함
                .jobCategories(member.getPrefJobs().stream()
                        .map(PreferredJob::getJobCategory)
                        .collect(Collectors.toList()))
                .locations(member.getPrefLocations().stream()
                        .map(PreferredRegion::getLocation)
                        .collect(Collectors.toList()))
                .build();

        // 4. 실제 DB 이력서 리스트 바인딩
        List<MemberResponseDTO.ResumeInfo> resumeInfos = member.getResumes().stream()
                .map(resume -> MemberResponseDTO.ResumeInfo.builder()
                        .resumeId(resume.getId())
                        .originalFilename(resume.getOriginal_filename()) // 스네이크 표기법 Getter 매핑
                        .storedFileUrl(resume.getStored_file_url())     // 다운로드에 사용할 S3 주소 등
                        .updatedAt(resume.getUpdated_at())
                        .isActive(resume.getIs_active() != null && resume.getIs_active())
                        .build())
                .collect(Collectors.toList());

        // 5. 전체 마이페이지 데이터 웅합 반환
        return MemberResponseDTO.MyPageDTO.builder()
                .profile(profile)
                .jobPreference(jobPreference)
                .resumes(resumeInfos)
                .build();
    }
}

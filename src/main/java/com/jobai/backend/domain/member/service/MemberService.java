package com.jobai.backend.domain.member.service;

import com.jobai.backend.domain.member.dto.MemberRequestDTO;
import com.jobai.backend.domain.member.dto.MemberResponseDTO;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.PreferredJob;
import com.jobai.backend.domain.member.entity.PreferredRegion;
import com.jobai.backend.domain.member.repository.MemberRepository;
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

    private Member findMemberOrThrow(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));
    }

    @Transactional
    public void updateMyJobPreferences(String email, MemberRequestDTO.UpdateJobPreferenceDTO request) {
        Member member = findMemberOrThrow(email);

        member.updateJobPreferences(
                request.getCareerType(),
                request.getJobCategories(),
                request.getLocations()
        );
    }

    // 온보딩 1단계: 희망 근무 지역 + 희망 채용 형태
    @Transactional
    public void updateOnboardingBasicInfo(String email, MemberRequestDTO.UpdateBasicInfoDTO request) {
        Member member = findMemberOrThrow(email);

        member.updateBasicInfo(request.getCareerType(), request.getLocations());
    }

    // 온보딩 2단계: 희망 직무
    @Transactional
    public void updateOnboardingJobCategory(String email, MemberRequestDTO.UpdateJobCategoryDTO request) {
        Member member = findMemberOrThrow(email);

        member.updateJobCategories(request.getJobCategories());
    }

    // 마이페이지의 모든 정보를 조회하는 함수
    public MemberResponseDTO.MyPageDTO getMyPageData(String email) {
        Member member = findMemberOrThrow(email);

        // 2. 프로필 정보 조립
        MemberResponseDTO.ProfileInfo profile = MemberResponseDTO.ProfileInfo.builder()
                .name(member.getName())
                .email(member.getEmail())
                .build();

        // 3. 선호 조건 정보 조립
        MemberResponseDTO.JobPreferenceInfo jobPreference = MemberResponseDTO.JobPreferenceInfo.builder()
                .careerType(List.copyOf(member.getCareerTypes()))
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
                        .originalFilename(resume.getOriginalFilename()) // 스네이크 표기법 Getter 매핑
                        .storedFileUrl(resume.getStoredFileUrl())     // 다운로드에 사용할 S3 주소 등
                        .updatedAt(resume.getUpdatedAt())
                        .isActive(resume.getIsActive() != null && resume.getIsActive())
                        .build())
                .collect(Collectors.toList());

        // 5. 전체 마이페이지 데이터 웅합 반환
        return MemberResponseDTO.MyPageDTO.builder()
                .profile(profile)
                .jobPreference(jobPreference)
                .resumes(resumeInfos)
                .build();
    }

    @Transactional // 더티체크를 활용한 이름변경 로직
    public void updateMemberName(String email, String newName) {
        Member member = findMemberOrThrow(email);

        member.update(newName);
    }
}

package com.jobai.backend.domain.application.service;

import com.jobai.backend.domain.application.dto.ApplicationRequestDTO;
import com.jobai.backend.domain.application.dto.ApplicationResponseDTO;
import com.jobai.backend.domain.application.entity.Application;
import com.jobai.backend.domain.application.entity.ApplicationStatus;
import com.jobai.backend.domain.application.repository.ApplicationRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

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

    // 클래스단에서 ReadOnly = True 해두고 메서드단에서 트랜잭션 안걸면
    // 영속성 컨텍스트에서는 지워지지만, 트랜잭션 커밋이 일어나지 않아서 delete가 안됨
    @Transactional // 이거 빼먹어서 왜 DB에서 안사라지나 했네 ㅋㅋ
    public void removeApplication(String email, Long applicationId) {
        // 1. 삭제할 공고 조회
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 지원 현황을 찾을 수 없습니다."));

        // 2. 로그인한 유저가 이 공고 주인인지 검증
        if (!application.getMember().getEmail().equals(email)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 지원 기록을 삭제할 권한이 없습니다.");
        }

        // 3. DB에서 삭제 수행 (Hard Delete)
        applicationRepository.delete(application);
    }

    // readOnly = true 설정
    public ApplicationResponseDTO.ApplicationListDTO getApplicationList(String email) {
        // 1. 해당 유저의 지원 현황 리스트 최신순 조회
        List<Application> applications = applicationRepository.findByMemberEmailOrderByAppliedAtDesc(email);

        // 2. 엔티티 리스트 -> DTO 리스트 변환
        List<ApplicationResponseDTO.ApplicationListItemDTO> itemDTOs = applications.stream()
                .map(app -> ApplicationResponseDTO.ApplicationListItemDTO.builder()
                        .applicationId(app.getId())
                        .companyName(app.getCompanyName())
                        .jobTitle(app.getJobTitle())
                        .status(app.getStatus())
                        .statusLabel(app.getStatus() != null ? app.getStatus().getDescription() : "") // Enum 내 한글 매핑 텍스트 활용
                        .appliedAt(app.getAppliedAt())
                        .interviewAt(app.getInterviewAt())
                        .memo(app.getMemo())
                        .build())
                .collect(Collectors.toList());

        // 3. 객체 조립 후 반환
        return ApplicationResponseDTO.ApplicationListDTO.builder()
                .applications(itemDTOs)
                .build();
    }

    public ApplicationResponseDTO.ApplicationSummaryDTO getApplicationSummary(String email) {
        // 1. 해당 유저의 모든 지원 현황 조회
        List<Application> allApplications = applicationRepository.findByMemberEmailOrderByAppliedAtDesc(email);

        // 2. 탈락 및 지원 예정 공고 필터링
        List<Application> validApplications = allApplications.stream()
                .filter(app -> app.getStatus() != ApplicationStatus.DOCUMENT_REJECTED
                        && app.getStatus() != ApplicationStatus.INTERVIEW_REJECTED
                        && app.getStatus() != ApplicationStatus.PLANNED)
                .toList();

        // 3. 예외 처리: 계산 대상 공고가 하나도 없는 경우 0% 리턴하여 에러(NaN) 방지
        if (validApplications.isEmpty()) {
            return ApplicationResponseDTO.ApplicationSummaryDTO.builder()
                    .averageProgress(0.0)
                    .totalCalculatedCount(0)
                    .build();
        }

        // 4. 진행도 점수 추출 및 평균 계산 (Stream API 활용)
        double averageProgress = validApplications.stream()
                .mapToInt(app -> app.getStatus().getProgress()) // Enum에 정의된 점수(10, 40, 70, 100) 가져오기
                .average() // 평균 계산 (OptionalDouble 반환)
                .orElse(0.0);

        // 반올림 처리 (예: 45.3333... -> 45)
        averageProgress = Math.round(averageProgress);

        // 5. 결과 DTO 반환
        return ApplicationResponseDTO.ApplicationSummaryDTO.builder()
                .averageProgress(averageProgress)
                .totalCalculatedCount(validApplications.size())
                .build();
    }

    public ApplicationResponseDTO.UpcomingScheduleListDTO getUpcomingSchedules(String email) {
        LocalDate today = LocalDate.now();

        // 1. 오늘 이후의 면접 일정 Top 3 조회
        List<Application> upcomingApplications =
                applicationRepository.findTop3ByMemberEmailAndInterviewAtGreaterThanEqualOrderByInterviewAtAsc(email, today);

        // 2. 엔티티 -> DTO 변환 및 디데이 연산
        List<ApplicationResponseDTO.UpcomingScheduleItemDTO> scheduleItems = upcomingApplications.stream()
                .map(app -> {
                    // 오늘과 면접일 사이의 남은 일수 계산
                    long daysLeft = ChronoUnit.DAYS.between(today, app.getInterviewAt());

                    return ApplicationResponseDTO.UpcomingScheduleItemDTO.builder()
                            .applicationId(app.getId())
                            .companyName(app.getCompanyName())
                            .jobTitle(app.getJobTitle())
                            .interviewAt(app.getInterviewAt())
                            .daysLeft(daysLeft)
                            .build();
                })
                .collect(Collectors.toList());

        // 3. 최종 리스트 DTO 조립
        return ApplicationResponseDTO.UpcomingScheduleListDTO.builder()
                .schedules(scheduleItems)
                .build();
    }
}
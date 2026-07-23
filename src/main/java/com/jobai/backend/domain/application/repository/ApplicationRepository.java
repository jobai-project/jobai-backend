package com.jobai.backend.domain.application.repository;

import com.jobai.backend.domain.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // 로그인한 유저의 이메일로 조회, 최신 지원일 순서대로 정렬해서 데이터를 가져옴.
    List<Application> findByMemberEmailOrderByAppliedAtDesc(String email);

    // 유저의 공고 중, 면접일이 '오늘 or 미래'인 일정을 가장 가까운 순으로 최대 3개만 조회.
    List<Application> findTop3ByMemberEmailAndInterviewAtGreaterThanEqualOrderByInterviewAtAsc(
            String email,
            LocalDate today
    );

    void deleteByMemberId(Long memberId);
}

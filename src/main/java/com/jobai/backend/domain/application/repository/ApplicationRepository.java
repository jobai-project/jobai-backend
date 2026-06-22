package com.jobai.backend.domain.application.repository;

import com.jobai.backend.domain.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // 로그인한 유저의 이메일로 조회, 최신 지원일 순서대로 정렬해서 데이터를 가져옴.
    List<Application> findByMemberEmailOrderByAppliedAtDesc(String email);
}

package com.jobai.backend.domain.member.repository;

import com.jobai.backend.domain.member.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    void deleteByMemberId(Long memberId);
}

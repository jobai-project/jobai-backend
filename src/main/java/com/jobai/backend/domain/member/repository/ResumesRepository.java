package com.jobai.backend.domain.member.repository;

import com.jobai.backend.domain.member.entity.Resumes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ResumesRepository extends JpaRepository<Resumes, Long> {

    List<Resumes> findByMemberEmailOrderByUpdatedAtDescIdDesc(String email);

    Optional<Resumes> findByMemberEmailAndIsActiveTrue(String email);

    @Modifying
    @Query("UPDATE Resumes r SET r.isActive = false WHERE r.member.id = :memberId AND r.id <> :excludeId")
    void deactivateOthersByMemberId(Long memberId, Long excludeId);
}

package com.jobai.backend.domain.application.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationStatus {
    PLANNED("지원 예정", 0), APPLIED("지원 완료", 10),
    DOCUMENT_PASSED("서류 합격", 40), INTERVIEW_PASSED("면접 합격", 70), FINAL_ACCEPTED("최종 합격", 100)
    , DOCUMENT_REJECTED("서류 탈락", 0), INTERVIEW_REJECTED("면접 탈락", 0);

    private final String description;
    private final int progress; // 각 단계별 누적 진행도 (%)

}

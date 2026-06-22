package com.jobai.backend.domain.application.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationStatus {
    PLANNED("지원 예정"), APPLIED("지원 완료"),
    DOCUMENT_PASSED("서류 합격"), INTERVIEW_PASSED("면접 합격"), FINAL_ACCEPTED("최종 합격")
    , DOCUMENT_REJECTED("서류 탈락"), INTERVIEW_REJECTED("면접 탈락");

    private final String description;

}

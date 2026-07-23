package com.jobai.backend.domain.member.event;

import java.util.List;

public record MemberWithdrawalCompletedEvent(List<String> resumeFileUrls) {

    public MemberWithdrawalCompletedEvent {
        resumeFileUrls = List.copyOf(resumeFileUrls);
    }
}

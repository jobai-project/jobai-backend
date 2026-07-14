package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.notification.dto.RealtimeNotificationPayload;
import com.jobai.backend.domain.notification.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 배치 점수 산출 후 임계값 이상 공고에 대한 알림 발송을 담당하는 공통 헬퍼.
 *
 * <p>Private/Public 배치 서비스에서 동일하게 사용하여 메시지 포맷팅·예외 처리 정책의 중복을 방지한다.
 * 알림 발송 실패 시에도 점수 산출에는 영향을 주지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchNotificationHelper {

    private final NotificationDispatchService notificationDispatchService;

    /** 임계값 이상 점수를 받은 공고 정보. 알림 메시지 구성에 사용된다. */
    public record ScoredPosting(String title, String company, int score, Long postingId, String linkPrefix) {
    }

    /**
     * 임계값 이상 공고가 있으면 사용자에게 묶음 알림을 발송한다.
     * 1건이면 해당 공고 제목·점수를 표시하고, 여러 건이면 최고 점수 공고 + 나머지 건수로 요약한다.
     *
     * @param member         알림 수신 대상 회원
     * @param aboveThreshold 임계값 이상 공고 목록
     * @param notificationType 알림 제목 (예: "새 추천 공고", "새 추천 공고 (공기업)")
     */
    public void sendIfNeeded(Member member, List<ScoredPosting> aboveThreshold, String notificationType) {
        if (aboveThreshold.isEmpty()) return;

        try {
            String message;
            String linkUrl;
            if (aboveThreshold.size() == 1) {
                ScoredPosting top = aboveThreshold.get(0);
                message = String.format("[%s] %s (매칭 %d점)", top.company(), top.title(), top.score());
                linkUrl = top.linkPrefix() + top.postingId();
            } else {
                ScoredPosting top = aboveThreshold.stream()
                        .max(Comparator.comparingInt(ScoredPosting::score))
                        .orElse(aboveThreshold.get(0));
                message = String.format("[%s] %s 외 %d건 (최고 %d점)",
                        top.company(), top.title(), aboveThreshold.size() - 1, top.score());
                linkUrl = "/jobs";
            }

            notificationDispatchService.notifyUser(
                    member.getEmail(),
                    RealtimeNotificationPayload.of("MATCH", notificationType, message, linkUrl)
            );
            log.info("[배치알림] {} — {}건 알림 발송", member.getEmail(), aboveThreshold.size());
        } catch (Exception e) {
            log.warn("[배치알림] 알림 발송 실패: email={}, error={}", member.getEmail(), e.getMessage());
        }
    }
}
